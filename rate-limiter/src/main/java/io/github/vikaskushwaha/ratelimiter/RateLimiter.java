package io.github.vikaskushwaha.ratelimiter;

/**
 * Core contract for all rate limiter implementations in this library.
 *
 * <p>Designed following the Interface Segregation Principle — callers depend
 * only on this interface, making it trivial to swap Token Bucket for Sliding
 * Window, or a local impl for a future Redis-backed distributed impl, without
 * touching any call site.
 *
 * <h2>Usage — two-liner integration</h2>
 * <pre>{@code
 * RateLimiter limiter = RateLimiterFactory.tokenBucket(100, 10); // 100 capacity, 10 tokens/sec
 * if (limiter.tryAcquire()) {
 *     // serve the request
 * } else {
 *     throw new RateLimitExceededException("Too many requests");
 * }
 * }</pre>
 *
 * @author Vikas Kushwaha
 * @version 1.0.0
 * @since 1.0.0
 */
public interface RateLimiter {

    /**
     * Attempts to acquire a single permit without blocking.
     *
     * @return {@code true} if the permit was granted; {@code false} if the
     *         rate limit has been exceeded and the caller should be rejected
     */
    boolean tryAcquire();

    /**
     * Attempts to acquire {@code permits} permits in a single atomic operation.
     *
     * <p>Either all {@code permits} are granted or none are — there is no
     * partial acquisition.
     *
     * @param permits number of permits to acquire; must be &gt; 0
     * @return {@code true} if all permits were granted; {@code false} otherwise
     * @throws IllegalArgumentException if {@code permits} is less than 1
     */
    boolean tryAcquire(int permits);

    /**
     * Acquires a single permit, throwing if the rate limit is exceeded.
     *
     * <p>This is a convenience method equivalent to:
     * <pre>{@code
     * if (!tryAcquire()) throw new RateLimitExceededException("...");
     * }</pre>
     *
     * @throws RateLimitExceededException if no permit is currently available
     */
    void acquire();

    /**
     * Returns the current observed request rate in requests per second,
     * computed over the last measurement window.
     *
     * <p>This value is an approximation and should be used for monitoring /
     * observability dashboards — not for control-flow decisions.
     *
     * @return estimated current rate (requests/second), always &ge; 0.0
     */
    double getCurrentRate();
}
