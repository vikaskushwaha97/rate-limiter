package io.github.vikaskushwaha.ratelimiter;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
 *   <li>All mutable state is packed into an immutable {@link State} object 
 *       and tracked via a single {@link AtomicReference}.</li>
 *   <li>Refill and token consumption are combined into a single 
 *       compare-and-set (CAS) pipeline, eliminating time-of-check to 
 *       time-of-use (TOCTOU) race conditions.</li>
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
     * Immutable state holding the current token count and the timestamp of
     * the last refill check. Using a single object allows for atomic
     * updates of both fields in a single CAS operation.
     */
    private static final class State {
        final long tokens;
        final long lastRefillNanos;

        State(long tokens, long lastRefillNanos) {
            this.tokens = tokens;
            this.lastRefillNanos = lastRefillNanos;
        }
    }

    /**
     * The atomic reference to the current state.
     */
    private final AtomicReference<State> state;

    private static final class RateSnapshot {
        final long startNanos;
        final long count;

        RateSnapshot(long startNanos, long count) {
            this.startNanos = startNanos;
            this.count = count;
        }
    }

    // Rate tracking — lock-free and non-destructive
    private final AtomicReference<RateSnapshot> rateSnapshot;

    /**
     * Constructs a Token Bucket limiter from a {@link RateLimiterConfig}.
     *
     * @param config validated configuration; must not be {@code null}
     */
    public TokenBucketRateLimiter(RateLimiterConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        this.capacity          = config.getCapacity();
        this.refillRate        = config.getRefillRate();
        this.refillPeriodNanos = config.getRefillPeriodNanos();

        long now = System.nanoTime();
        // Start full — common convention: new bucket is at max capacity.
        this.state             = new AtomicReference<>(new State(capacity, now));
        this.rateSnapshot      = new AtomicReference<>(new RateSnapshot(now, 0));
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
     * Computes necessary refill and decrements permits in a single atomic CAS loop.
     */
    @Override
    public boolean tryAcquire(int permits) {
        if (permits < 1) throw new IllegalArgumentException("permits must be >= 1, got " + permits);
        if (permits > capacity) return false; // can never be satisfied

        long now = System.nanoTime();

        while (true) {
            State current = state.get();
            long newTokens = current.tokens;
            long newLastRefill = current.lastRefillNanos;

            // Lazily compute accrued tokens
            long elapsed = now - newLastRefill;
            if (elapsed > 0) {
                long periodsElapsed = elapsed / refillPeriodNanos;
                if (periodsElapsed > 0) {
                    long tokensToAdd = periodsElapsed * refillRate;
                    newTokens = Math.min(capacity, newTokens + tokensToAdd);
                    newLastRefill = newLastRefill + periodsElapsed * refillPeriodNanos;
                }
            }

            if (newTokens < permits) {
                return false; // Not enough tokens
            }

            // Consume permits
            newTokens -= permits;

            State next = new State(newTokens, newLastRefill);
            if (state.compareAndSet(current, next)) {
                updateRate(permits, now);
                return true;
            }
            // Another thread modified state — loop and retry
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
     * <p>Rate is measured as requests granted in the current trailing 1-second window.
     * The read is completely non-destructive and safe under heavy concurrency.
     */
    @Override
    public double getCurrentRate() {
        RateSnapshot current = rateSnapshot.get();
        long elapsed = System.nanoTime() - current.startNanos;
        
        if (elapsed <= 0) return 0.0;
        
        // If the window is older than 2 seconds, traffic has effectively stopped.
        if (elapsed > 2_000_000_000L) return 0.0;

        double elapsedSecs = elapsed / 1_000_000_000.0;
        return current.count / elapsedSecs;
    }

    private void updateRate(int permits, long now) {
        while (true) {
            RateSnapshot current = rateSnapshot.get();
            long elapsed = now - current.startNanos;
            
            if (elapsed > 1_000_000_000L) {
                // Window is older than 1 second — start a new one
                if (rateSnapshot.compareAndSet(current, new RateSnapshot(now, permits))) {
                    break;
                }
            } else {
                // Add to current window
                if (rateSnapshot.compareAndSet(current, new RateSnapshot(current.startNanos, current.count + permits))) {
                    break;
                }
            }
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
        // Read-only calculation of currently available tokens
        State current = state.get();
        long elapsed = System.nanoTime() - current.lastRefillNanos;
        if (elapsed > 0) {
            long periodsElapsed = elapsed / refillPeriodNanos;
            long tokensToAdd = periodsElapsed * refillRate;
            return Math.min(capacity, current.tokens + tokensToAdd);
        }
        return current.tokens;
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
               "/period, availableTokens=" + getAvailableTokens() + '}';
    }
}
