/**
 * Java Rate Limiter Library — Core Package.
 *
 * <p>This package provides two production-grade rate limiting algorithm
 * implementations and supporting infrastructure:
 *
 * <ul>
 *   <li>{@link io.github.vikaskushwaha.ratelimiter.RateLimiter} — the core
 *       interface; all callers should depend only on this type.</li>
 *   <li>{@link io.github.vikaskushwaha.ratelimiter.TokenBucketRateLimiter} —
 *       lock-free Token Bucket using AtomicLong CAS. Ideal for burst-tolerant
 *       limiting.</li>
 *   <li>{@link io.github.vikaskushwaha.ratelimiter.SlidingWindowRateLimiter} —
 *       circular-bucket Sliding Window Counter. Ideal for smooth, time-based
 *       limiting.</li>
 *   <li>{@link io.github.vikaskushwaha.ratelimiter.RateLimiterFactory} —
 *       static factory; the primary entry point for library consumers.</li>
 *   <li>{@link io.github.vikaskushwaha.ratelimiter.RateLimiterConfig} —
 *       immutable config built via a fluent builder.</li>
 *   <li>{@link io.github.vikaskushwaha.ratelimiter.RateLimitExceededException} —
 *       unchecked exception thrown by {@code acquire()} when the limit is hit.</li>
 * </ul>
 *
 * <h2>Two-liner integration</h2>
 * <pre>{@code
 * RateLimiter limiter = RateLimiterFactory.tokenBucket(100, 10);
 * if (!limiter.tryAcquire()) throw new RateLimitExceededException("Too many requests");
 * }</pre>
 *
 * <h2>Zero runtime dependencies</h2>
 * <p>This library has no runtime dependencies. Only {@code java.util.concurrent}
 * primitives are used.
 *
 * @author Vikas Kushwaha
 * @version 1.0.0
 */
package io.github.vikaskushwaha.ratelimiter;
