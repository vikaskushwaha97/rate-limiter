package io.github.vikaskushwaha.ratelimiter;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Immutable configuration object for {@link RateLimiter} instances.
 *
 * <p>Use the nested {@link Builder} to construct instances. All validation
 * is performed at {@link Builder#build()} time so that invalid configs are
 * caught at startup, never at request time.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * RateLimiterConfig config = RateLimiterConfig.builder()
 *     .capacity(1000)
 *     .refillRate(100)
 *     .refillPeriod(1, TimeUnit.SECONDS)
 *     .windowDuration(1, TimeUnit.SECONDS)
 *     .windowBuckets(10)
 *     .build();
 * }</pre>
 *
 * @author Vikas Kushwaha
 * @version 1.0.0
 * @since 1.0.0
 */
public final class RateLimiterConfig {

    /** Maximum number of tokens the bucket can hold (Token Bucket). */
    private final long capacity;

    /** Number of tokens added per {@code refillPeriodNanos} (Token Bucket). */
    private final long refillRate;

    /** Period over which {@code refillRate} tokens are added, in nanoseconds. */
    private final long refillPeriodNanos;

    /** Total duration of the sliding window, in nanoseconds (Sliding Window). */
    private final long windowDurationNanos;

    /** Number of sub-buckets that subdivide the sliding window (Sliding Window). */
    private final int windowBuckets;

    private RateLimiterConfig(Builder b) {
        this.capacity           = b.capacity;
        this.refillRate         = b.refillRate;
        this.refillPeriodNanos  = b.refillPeriodNanos;
        this.windowDurationNanos= b.windowDurationNanos;
        this.windowBuckets      = b.windowBuckets;
    }

    /**
     * Creates a new {@link Builder} with sensible defaults.
     *
     * @return a fresh builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /** @return maximum bucket capacity (Token Bucket) */
    public long getCapacity() { return capacity; }

    /** @return token refill rate per period (Token Bucket) */
    public long getRefillRate() { return refillRate; }

    /** @return refill period in nanoseconds (Token Bucket) */
    public long getRefillPeriodNanos() { return refillPeriodNanos; }

    /** @return sliding window duration in nanoseconds (Sliding Window) */
    public long getWindowDurationNanos() { return windowDurationNanos; }

    /** @return number of sub-buckets in the sliding window (Sliding Window) */
    public int getWindowBuckets() { return windowBuckets; }

    @Override
    public String toString() {
        return "RateLimiterConfig{" +
               "capacity=" + capacity +
               ", refillRate=" + refillRate +
               ", refillPeriodNanos=" + refillPeriodNanos +
               ", windowDurationNanos=" + windowDurationNanos +
               ", windowBuckets=" + windowBuckets +
               '}';
    }

    /**
     * Two configs are equal if all five fields match.
     * Follows the contract specified by {@link Object#equals(Object)}.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RateLimiterConfig)) return false;
        RateLimiterConfig that = (RateLimiterConfig) o;
        return capacity == that.capacity
            && refillRate == that.refillRate
            && refillPeriodNanos == that.refillPeriodNanos
            && windowDurationNanos == that.windowDurationNanos
            && windowBuckets == that.windowBuckets;
    }

    /**
     * Hash code consistent with {@link #equals(Object)}.
     */
    @Override
    public int hashCode() {
        return Objects.hash(capacity, refillRate, refillPeriodNanos,
                            windowDurationNanos, windowBuckets);
    }

    // =========================================================================
    // Builder
    // =========================================================================

    /**
     * Fluent builder for {@link RateLimiterConfig}.
     *
     * <p>All setters return {@code this} for chaining. Call {@link #build()}
     * to obtain an immutable config. Validation errors throw
     * {@link IllegalArgumentException} immediately so bad configs are caught
     * at startup rather than at request time.
     */
    public static final class Builder {

        private long capacity           = 100;
        private long refillRate         = 10;
        private long refillPeriodNanos  = TimeUnit.SECONDS.toNanos(1);
        private long windowDurationNanos= TimeUnit.SECONDS.toNanos(1);
        private int  windowBuckets      = 10;

        private Builder() {}

        /**
         * Sets the maximum token capacity for the Token Bucket algorithm.
         *
         * @param capacity must be &gt; 0
         * @return this builder
         */
        public Builder capacity(long capacity) {
            this.capacity = capacity;
            return this;
        }

        /**
         * Sets the number of tokens refilled per {@link #refillPeriod}.
         *
         * @param refillRate must be &gt; 0
         * @return this builder
         */
        public Builder refillRate(long refillRate) {
            this.refillRate = refillRate;
            return this;
        }

        /**
         * Sets the period over which {@link #refillRate} tokens are refilled.
         *
         * @param duration must be &gt; 0
         * @param unit     must not be null
         * @return this builder
         */
        public Builder refillPeriod(long duration, TimeUnit unit) {
            this.refillPeriodNanos = unit.toNanos(duration);
            return this;
        }

        /**
         * Sets the total duration of the sliding window.
         *
         * @param duration must be &gt; 0
         * @param unit     must not be null
         * @return this builder
         */
        public Builder windowDuration(long duration, TimeUnit unit) {
            this.windowDurationNanos = unit.toNanos(duration);
            return this;
        }

        /**
         * Sets the number of sub-buckets subdividing the sliding window.
         * More buckets means finer granularity but more memory.
         *
         * @param buckets must be &ge; 1
         * @return this builder
         */
        public Builder windowBuckets(int buckets) {
            this.windowBuckets = buckets;
            return this;
        }

        /**
         * Validates all fields and returns an immutable {@link RateLimiterConfig}.
         *
         * @return immutable config
         * @throws IllegalArgumentException if any field is out of range
         */
        public RateLimiterConfig build() {
            if (capacity <= 0)            throw new IllegalArgumentException("capacity must be > 0, got " + capacity);
            if (refillRate <= 0)          throw new IllegalArgumentException("refillRate must be > 0, got " + refillRate);
            if (refillPeriodNanos <= 0)   throw new IllegalArgumentException("refillPeriod must be > 0");
            if (windowDurationNanos <= 0) throw new IllegalArgumentException("windowDuration must be > 0");
            if (windowBuckets < 1)        throw new IllegalArgumentException("windowBuckets must be >= 1, got " + windowBuckets);
            return new RateLimiterConfig(this);
        }
    }
}
