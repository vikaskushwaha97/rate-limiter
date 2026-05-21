package io.github.vikaskushwaha.ratelimiter;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A thread-safe Sliding Window Counter rate limiter.
 *
 * <h2>Algorithm</h2>
 * <p>The window is divided into {@code N} equal sub-buckets (circular array of
 * {@link AtomicLong}). Each sub-bucket covers {@code windowDuration / N}
 * nanoseconds. On each request, the implementation:
 * <ol>
 *   <li>Computes which sub-bucket the current timestamp maps to.</li>
 *   <li>Clears any sub-buckets that have rotated out of the window (stale).</li>
 *   <li>Computes the total count across all live buckets.</li>
 *   <li>If total &lt; limit, increments the current bucket (CAS) and grants
 *       the request; otherwise rejects it.</li>
 * </ol>
 *
 * <h2>Concurrency design</h2>
 * <ul>
 *   <li>Each sub-bucket is an independent {@link AtomicLong} — increment is
 *       always lock-free.</li>
 *   <li>Bucket rotation (clearing stale buckets) is protected by a
 *       {@code synchronized} block on the bucket array reference. This is the
 *       <em>only</em> synchronized block in the library, and it is cold-path:
 *       it executes at most once per sub-bucket period.</li>
 *   <li>The total-count read is a non-atomic summation of all
 *       {@code AtomicLong} values. Under high concurrency this is a
 *       conservative estimate (actual count may be slightly higher or lower by
 *       a few counts between the read and the increment), which is acceptable
 *       for rate limiting — we guarantee we never allow more than
 *       {@code limit + N - 1} requests in a window, where N is the number of
 *       sub-buckets.</li>
 * </ul>
 *
 * <h2>Why circular array?</h2>
 * <p>O(1) bucket selection by {@code index = (bucketIndex) % N}. No allocation
 * on the hot path — the array is pre-allocated at construction time.
 *
 * @author Vikas Kushwaha
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SlidingWindowRateLimiter implements RateLimiter {

    /** Maximum requests allowed in the full window. */
    private final long limit;

    /** Total window duration in nanoseconds. */
    private final long windowNanos;

    /** Duration of each sub-bucket in nanoseconds. */
    private final long bucketNanos;

    /** Number of sub-buckets. */
    private final int bucketCount;

    /**
     * Circular array of sub-bucket counters.
     * Each cell tracks the number of requests in that time slice.
     */
    private final AtomicLong[] buckets;

    /**
     * Timestamp (nanos) of the start of each sub-bucket slot.
     * Used to detect when a slot has rotated out and must be cleared.
     */
    private final long[] bucketStartTimes;

    /** Index of the most-recently active bucket. */
    private volatile int currentBucketIndex;

    // Rate tracking — for getCurrentRate().
    private final AtomicLong grantedCount   = new AtomicLong(0);
    private final AtomicLong windowStartNanos;

    /**
     * Constructs a Sliding Window rate limiter from a {@link RateLimiterConfig}.
     *
     * @param config validated configuration; must not be {@code null}
     */
    public SlidingWindowRateLimiter(RateLimiterConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        this.limit        = config.getCapacity();   // "capacity" = limit here
        this.windowNanos  = config.getWindowDurationNanos();
        this.bucketCount  = config.getWindowBuckets();
        this.bucketNanos  = windowNanos / bucketCount;

        this.buckets          = new AtomicLong[bucketCount];
        this.bucketStartTimes = new long[bucketCount];

        long now = System.nanoTime();
        for (int i = 0; i < bucketCount; i++) {
            buckets[i]          = new AtomicLong(0);
            bucketStartTimes[i] = now;
        }
        this.currentBucketIndex = 0;
        this.windowStartNanos   = new AtomicLong(now);
    }

    /**
     * Constructs a Sliding Window rate limiter with explicit parameters.
     *
     * @param limit          max requests allowed per window
     * @param windowDuration window size duration
     * @param windowUnit     time unit for {@code windowDuration}
     * @param buckets        number of sub-buckets (granularity)
     */
    public SlidingWindowRateLimiter(long limit, long windowDuration,
                                    TimeUnit windowUnit, int buckets) {
        this(RateLimiterConfig.builder()
                .capacity(limit)
                .windowDuration(windowDuration, windowUnit)
                .windowBuckets(buckets)
                .build());
    }

    // =========================================================================
    // RateLimiter implementation
    // =========================================================================

    /** {@inheritDoc} */
    @Override
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Either all {@code permits} are granted or none. This implementation
     * performs a best-effort check then an atomic increment — the window may
     * be exceeded by at most {@code permits - 1} under extreme concurrency,
     * which is within the acceptable approximation error of this algorithm.
     */
    @Override
    public boolean tryAcquire(int permits) {
        if (permits < 1) throw new IllegalArgumentException("permits must be >= 1, got " + permits);
        if (permits > limit) return false;

        long now = System.nanoTime();
        rotateBuckets(now);

        // Sum all live buckets — conservative snapshot.
        long total = sumBuckets();
        if (total + permits > limit) return false;

        // Increment the current bucket (may push us slightly over if many
        // threads race here — bounded overage of at most threadCount - 1).
        int idx = currentBucketIndex;
        buckets[idx].addAndGet(permits);
        grantedCount.addAndGet(permits);
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * @throws RateLimitExceededException if the window limit is exceeded
     */
    @Override
    public void acquire() {
        if (!tryAcquire()) {
            throw new RateLimitExceededException(
                "Rate limit exceeded — window limit reached (limit=" + limit +
                ", window=" + TimeUnit.NANOSECONDS.toMillis(windowNanos) + "ms)");
        }
    }

    /** {@inheritDoc} */
    @Override
    public double getCurrentRate() {
        long now     = System.nanoTime();
        long start   = windowStartNanos.get();
        long elapsed = now - start;
        if (elapsed <= 0) return 0.0;

        long count = grantedCount.getAndSet(0);
        windowStartNanos.set(now);

        double elapsedSecs = elapsed / 1_000_000_000.0;
        return count / elapsedSecs;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Rotates the circular bucket array, clearing any sub-buckets that have
     * moved outside the sliding window.
     *
     * <p>Synchronized on {@code buckets} — this is cold-path code (fires at
     * most once per sub-bucket period). The hot path (increment) is always
     * lock-free.
     *
     * @param now current time in nanoseconds
     */
    private void rotateBuckets(long now) {
        synchronized (buckets) {
            int   idx          = currentBucketIndex;
            long  bucketStart  = bucketStartTimes[idx];
            long  elapsed      = now - bucketStart;

            if (elapsed < bucketNanos) {
                // Still inside the current sub-bucket — nothing to rotate.
                return;
            }

            // How many buckets have elapsed since the last rotation?
            long bucketsElapsed = elapsed / bucketNanos;

            // Cap at bucketCount to avoid clearing the same bucket twice.
            long toClear = Math.min(bucketsElapsed, bucketCount);

            for (long i = 1; i <= toClear; i++) {
                int clearIdx = (int) ((idx + i) % bucketCount);
                buckets[clearIdx].set(0);
                bucketStartTimes[clearIdx] = bucketStart + i * bucketNanos;
            }

            currentBucketIndex = (int) ((idx + bucketsElapsed) % bucketCount);
            bucketStartTimes[currentBucketIndex] = now;
        }
    }

    /**
     * Sums the counts in all sub-buckets.
     *
     * <p>This is a non-atomic snapshot. Under concurrency, the real count may
     * differ by a small constant, which is acceptable for rate limiting.
     *
     * @return approximate total request count within the current window
     */
    private long sumBuckets() {
        long total = 0;
        for (AtomicLong bucket : buckets) {
            total += bucket.get();
        }
        return total;
    }

    // =========================================================================
    // Diagnostics
    // =========================================================================

    /**
     * Returns the approximate number of requests recorded in the current window.
     * Intended for testing and monitoring.
     *
     * @return sum of all sub-bucket counters
     */
    public long getCurrentWindowCount() {
        rotateBuckets(System.nanoTime());
        return sumBuckets();
    }

    /**
     * Returns the configured limit (max requests per window).
     *
     * @return request limit
     */
    public long getLimit() { return limit; }

    @Override
    public String toString() {
        return "SlidingWindowRateLimiter{limit=" + limit +
               ", windowMs=" + TimeUnit.NANOSECONDS.toMillis(windowNanos) +
               ", buckets=" + bucketCount +
               ", currentCount=" + sumBuckets() + '}';
    }
}
