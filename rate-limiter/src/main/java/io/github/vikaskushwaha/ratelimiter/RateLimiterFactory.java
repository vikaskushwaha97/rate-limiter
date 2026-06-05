package io.github.vikaskushwaha.ratelimiter;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Static factory for creating {@link RateLimiter} instances.
 *
 * <p>This is the primary entry point for library consumers. All methods return
 * the {@link RateLimiter} interface type — callers are decoupled from the
 * concrete implementation and can swap algorithms without any code changes.
 *
 * <h2>Design rationale — static factory vs. constructor</h2>
 * <ul>
 *   <li>Named factory methods ({@code tokenBucket}, {@code slidingWindow})
 *       communicate intent better than constructors.</li>
 *   <li>They return the interface type, hiding the impl class — classic
 *       "program to interfaces" principle.</li>
 *   <li>Makes it easy to add caching, pooling, or decoration in future
 *       versions without breaking callers.</li>
 * </ul>
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * // Token Bucket: 100 requests burst, refill 10 req/sec
 * RateLimiter tb = RateLimiterFactory.tokenBucket(100, 10);
 *
 * // Sliding Window: max 60 requests per minute, 6 sub-buckets (10s each)
 * RateLimiter sw = RateLimiterFactory.slidingWindow(60, 1, TimeUnit.MINUTES, 6);
 *
 * // From config (for advanced tuning)
 * RateLimiterConfig config = RateLimiterConfig.builder()
 *     .capacity(500).refillRate(50).refillPeriod(1, TimeUnit.SECONDS).build();
 * RateLimiter custom = RateLimiterFactory.tokenBucket(config);
 * }</pre>
 *
 * @author Vikas Kushwaha
 * @version 1.0.0
 * @since 1.0.0
 */
public final class RateLimiterFactory {

    /** Utility class — not instantiable. */
    private RateLimiterFactory() {
        throw new AssertionError("RateLimiterFactory is a static utility class");
    }

    // =========================================================================
    // Token Bucket factory methods
    // =========================================================================

    /**
     * Creates a Token Bucket limiter with the given capacity and refill rate
     * (tokens per second).
     *
     * @param capacity   maximum tokens the bucket can hold; must be &gt; 0
     * @param refillRate tokens added per second; must be &gt; 0
     * @return a configured {@link TokenBucketRateLimiter}
     */
    public static RateLimiter tokenBucket(long capacity, long refillRate) {
        return new TokenBucketRateLimiter(capacity, refillRate, 1, TimeUnit.SECONDS);
    }

    /**
     * Creates a Token Bucket limiter with full control over refill period.
     *
     * @param capacity         maximum tokens; must be &gt; 0
     * @param refillRate       tokens added per period; must be &gt; 0
     * @param refillPeriod     duration of one refill period; must be &gt; 0
     * @param refillPeriodUnit time unit for {@code refillPeriod}
     * @return a configured {@link TokenBucketRateLimiter}
     */
    public static RateLimiter tokenBucket(long capacity, long refillRate,
                                          long refillPeriod, TimeUnit refillPeriodUnit) {
        return new TokenBucketRateLimiter(capacity, refillRate, refillPeriod, refillPeriodUnit);
    }

    /**
     * Creates a Token Bucket limiter from a {@link RateLimiterConfig}.
     *
     * @param config pre-built configuration; must not be {@code null}
     * @return a configured {@link TokenBucketRateLimiter}
     */
    public static RateLimiter tokenBucket(RateLimiterConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        return new TokenBucketRateLimiter(config);
    }

    // =========================================================================
    // Sliding Window factory methods
    // =========================================================================

    /**
     * Creates a Sliding Window limiter with the given limit per window.
     *
     * <p>Defaults to 10 sub-buckets, giving 100ms granularity for a 1-second
     * window.
     *
     * @param limit          maximum requests allowed in the window; must be &gt; 0
     * @param windowDuration duration of the sliding window; must be &gt; 0
     * @param windowUnit     time unit for {@code windowDuration}
     * @return a configured {@link SlidingWindowRateLimiter}
     */
    public static RateLimiter slidingWindow(long limit, long windowDuration, TimeUnit windowUnit) {
        return new SlidingWindowRateLimiter(limit, windowDuration, windowUnit, 10);
    }

    /**
     * Creates a Sliding Window limiter with explicit sub-bucket count.
     *
     * <p>More buckets = finer granularity but more memory.
     * Typical values: 10 (coarse), 60 (per-second for a minute window).
     *
     * @param limit          maximum requests allowed in the window; must be &gt; 0
     * @param windowDuration duration of the sliding window; must be &gt; 0
     * @param windowUnit     time unit for {@code windowDuration}
     * @param buckets        number of sub-buckets; must be &ge; 1
     * @return a configured {@link SlidingWindowRateLimiter}
     */
    public static RateLimiter slidingWindow(long limit, long windowDuration,
                                            TimeUnit windowUnit, int buckets) {
        return new SlidingWindowRateLimiter(limit, windowDuration, windowUnit, buckets);
    }

    /**
     * Creates a Sliding Window limiter from a {@link RateLimiterConfig}.
     *
     * @param config pre-built configuration; must not be {@code null}
     * @return a configured {@link SlidingWindowRateLimiter}
     */
    public static RateLimiter slidingWindow(RateLimiterConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        return new SlidingWindowRateLimiter(config);
    }
}
