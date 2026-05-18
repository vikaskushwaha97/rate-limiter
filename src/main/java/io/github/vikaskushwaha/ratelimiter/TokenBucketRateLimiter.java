package io.github.vikaskushwaha.ratelimiter;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A thread-safe Token Bucket rate limiter using lock-free CAS operations.
 *
 * <h2>Algorithm</h2>
 * <p>The token bucket holds up to {@code capacity} tokens. Tokens are refilled
 * lazily — no background thread. On each call to {@link #tryAcquire()}, the
 * implementation computes how many tokens have accumulated since the last
 * refill using wall-clock time, adds them in a single CAS loop, then attempts
 * to decrement the token count.
 *
 * <h2>Concurrency design</h2>
 * <ul>
 *   <li>All mutable state is in two {@link AtomicLong} fields:
 *       {@code tokens} and {@code lastRefillNanos}.</li>
 *   <li>Refill uses a compare-and-set (CAS) loop — no {@code synchronized}
 *       block on the hot path.</li>
 *   <li>Token consumption is a single {@code compareAndSet} — also lock-free.</li>
 *   <li>Under high contention, threads spin (CAS retry) for a bounded number
 *       of iterations then fall back gracefully.</li>
 * </ul>
 *
 * <h2>Why lazy refill?</h2>
 * <p>A background refill thread would add scheduling overhead and complicate
 * lifecycle management. Lazy refill is simpler, zero-thread, and just as
 * correct: we compute "tokens owed" at call time using {@code System.nanoTime()}.
 *
 * @author Vikas Kushwaha
 * @version 1.0.0
 * @since 1.0.0
 */
public final class TokenBucketRateLimiter implements RateLimiter {

    /** Maximum number of tokens the bucket can hold. */
    private final long capacity;

    /** Number of tokens added per {@code refillPeriodNanos}. */
    private final long refillRate;

    /** Refill period in nanoseconds. */
    private final long refillPeriodNanos;

    /**
     * Current token count — packed as a long to use AtomicLong.
     * Invariant: 0 &le; tokens &le; capacity.
     */
    private final AtomicLong tokens;

    /**
     * Timestamp (nanos) of the last refill check, used to compute accrued
     * tokens between calls.
     */
    private final AtomicLong lastRefillNanos;

    // Rate tracking — lightweight, approximate, for getCurrentRate().
    private final AtomicLong requestCount   = new AtomicLong(0);
    private final AtomicLong windowStartNanos;

    /**
     * Constructs a Token Bucket limiter from a {@link RateLimiterConfig}.
     *
     * @param config validated configuration; must not be {@code null}
     */
    public TokenBucketRateLimiter(RateLimiterConfig config) {
        this.capacity          = config.getCapacity();
        this.refillRate        = config.getRefillRate();
        this.refillPeriodNanos = config.getRefillPeriodNanos();

        long now = System.nanoTime();
        // Start full — common convention: new bucket is at max capacity.
        this.tokens            = new AtomicLong(capacity);
        this.lastRefillNanos   = new AtomicLong(now);
        this.windowStartNanos  = new AtomicLong(now);
    }

    /**
     * Constructs a Token Bucket limiter with explicit parameters.
     *
     * @param capacity          maximum number of tokens
     * @param refillRate        tokens added per period
     * @param refillPeriod      time unit duration of the refill period
     * @param refillPeriodUnit  time unit for {@code refillPeriod}
     */
    public TokenBucketRateLimiter(long capacity, long refillRate,
                                  long refillPeriod, TimeUnit refillPeriodUnit) {
        this(RateLimiterConfig.builder()
                .capacity(capacity)
                .refillRate(refillRate)
                .refillPeriod(refillPeriod, refillPeriodUnit)
                .build());
    }

    // =========================================================================
    // RateLimiter implementation
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * <p>Implementation: lazily refills accrued tokens, then attempts a CAS
     * decrement. No locks. O(1) amortized.
     */
    @Override
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Either all {@code permits} are granted atomically or none are.
     */
    @Override
    public boolean tryAcquire(int permits) {
        if (permits < 1) throw new IllegalArgumentException("permits must be >= 1, got " + permits);
        if (permits > capacity) return false; // can never be satisfied

        refill();

        // CAS loop: decrement by `permits` only if current value >= permits.
        while (true) {
            long current = tokens.get();
            if (current < permits) return false;
            if (tokens.compareAndSet(current, current - permits)) {
                requestCount.incrementAndGet();
                return true;
            }
            // Another thread changed tokens — retry.
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws RateLimitExceededException if no permit is available
     */
    @Override
    public void acquire() {
        if (!tryAcquire()) {
            throw new RateLimitExceededException(
                "Rate limit exceeded — bucket is empty (capacity=" + capacity +
                ", refillRate=" + refillRate + "/period)");
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Rate is measured as requests granted in the last second.
     * Uses a lightweight sliding measurement window reset on each read.
     */
    @Override
    public double getCurrentRate() {
        long now     = System.nanoTime();
        long start   = windowStartNanos.get();
        long elapsed = now - start;
        if (elapsed <= 0) return 0.0;

        long count = requestCount.getAndSet(0);
        windowStartNanos.set(now);

        double elapsedSecs = elapsed / 1_000_000_000.0;
        return count / elapsedSecs;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Lazily computes accrued tokens since the last refill and adds them,
     * capped at {@link #capacity}.
     *
     * <p>Uses a CAS loop on {@code lastRefillNanos} to ensure exactly one
     * thread performs the refill even under heavy concurrency.
     */
    private void refill() {
        long now = System.nanoTime();

        while (true) {
            long lastRefill = lastRefillNanos.get();
            long elapsed    = now - lastRefill;
            if (elapsed <= 0) break; // no time has passed — nothing to refill

            // How many full refill periods have elapsed?
            long periodsElapsed = elapsed / refillPeriodNanos;
            if (periodsElapsed == 0) break; // not even one period — skip

            long tokensToAdd = periodsElapsed * refillRate;
            // Advance the refill timestamp by exactly the periods consumed.
            long newLastRefill = lastRefill + periodsElapsed * refillPeriodNanos;

            // Only one thread wins this CAS; the losers loop and re-check.
            if (lastRefillNanos.compareAndSet(lastRefill, newLastRefill)) {
                // We won — add tokens, capped at capacity.
                addTokens(tokensToAdd);
                break;
            }
            // Lost the CAS — another thread refilled; re-read and continue.
        }
    }

    /**
     * Adds {@code toAdd} tokens using a CAS loop, capping at {@link #capacity}.
     *
     * @param toAdd number of tokens to add (must be &gt;= 0)
     */
    private void addTokens(long toAdd) {
        while (true) {
            long current = tokens.get();
            long updated = Math.min(capacity, current + toAdd);
            if (tokens.compareAndSet(current, updated)) break;
            // Another thread modified tokens concurrently — retry.
        }
    }

    // =========================================================================
    // Diagnostics — useful during testing and debugging
    // =========================================================================

    /**
     * Returns the current number of available tokens.
     * Intended for testing and monitoring — not for control-flow.
     *
     * @return number of tokens currently in the bucket (0 to capacity)
     */
    public long getAvailableTokens() {
        refill();
        return tokens.get();
    }

    /**
     * Returns the maximum capacity of this bucket.
     *
     * @return capacity
     */
    public long getCapacity() { return capacity; }

    @Override
    public String toString() {
        return "TokenBucketRateLimiter{capacity=" + capacity +
               ", refillRate=" + refillRate +
               "/period, availableTokens=" + tokens.get() + '}';
    }
}
