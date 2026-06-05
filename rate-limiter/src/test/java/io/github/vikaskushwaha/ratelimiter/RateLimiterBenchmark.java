package io.github.vikaskushwaha.ratelimiter;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * JMH micro-benchmarks for {@link TokenBucketRateLimiter} and
 * {@link SlidingWindowRateLimiter}.
 *
 * <h2>Running the benchmarks</h2>
 * <pre>
 * mvn package -DskipTests
 * java -jar target/rate-limiter-1.0.0-benchmarks.jar -wi 3 -i 5 -f 1 -rf json -rff benchmark-results.json
 * </pre>
 *
 * <h2>JMH annotations used</h2>
 * <ul>
 *   <li>{@link BenchmarkMode} — {@code Throughput} (ops/ms) + {@code AverageTime} (ns/op)</li>
 *   <li>{@link Threads} — tested at 1, 4, 8, 16 threads</li>
 *   <li>{@link State}{@code (Scope.Benchmark)} — one shared limiter across all threads,
 *       which is the realistic production scenario</li>
 * </ul>
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsPrepend = {"-server", "-XX:+UseG1GC"})
@State(Scope.Benchmark)
public class RateLimiterBenchmark {

    // =========================================================================
    // Shared state — one limiter per benchmark, shared across all threads
    // =========================================================================

    /**
     * Token Bucket with very high capacity/refill so we're benchmarking
     * the lock-free CAS path, not the "bucket is empty" rejection path.
     */
    private TokenBucketRateLimiter tokenBucket;

    /**
     * Sliding Window with very high limit so we're benchmarking the
     * hot increment path, not rotation.
     */
    private SlidingWindowRateLimiter slidingWindow;

    @Setup(Level.Trial)
    public void setUp() {
        // Effectively unlimited — benchmark the algorithmic overhead, not throttling
        tokenBucket   = new TokenBucketRateLimiter(Long.MAX_VALUE / 2, Long.MAX_VALUE / 4, 1, TimeUnit.SECONDS);
        slidingWindow = new SlidingWindowRateLimiter(Long.MAX_VALUE / 2, 1, TimeUnit.HOURS, 10);
    }

    // =========================================================================
    // Token Bucket benchmarks
    // =========================================================================

    /**
     * Baseline: single-threaded Token Bucket {@code tryAcquire()} throughput.
     * This is the ceiling — no contention overhead.
     */
    @Benchmark
    @Threads(1)
    public void tokenBucket_1Thread(Blackhole bh) {
        bh.consume(tokenBucket.tryAcquire());
    }

    /**
     * 4-thread Token Bucket throughput — light contention.
     */
    @Benchmark
    @Threads(4)
    public void tokenBucket_4Threads(Blackhole bh) {
        bh.consume(tokenBucket.tryAcquire());
    }

    /**
     * 8-thread Token Bucket throughput — medium contention.
     * This is the primary resume benchmark number.
     */
    @Benchmark
    @Threads(8)
    public void tokenBucket_8Threads(Blackhole bh) {
        bh.consume(tokenBucket.tryAcquire());
    }

    /**
     * 16-thread Token Bucket throughput — high contention.
     */
    @Benchmark
    @Threads(16)
    public void tokenBucket_16Threads(Blackhole bh) {
        bh.consume(tokenBucket.tryAcquire());
    }

    // =========================================================================
    // Sliding Window benchmarks
    // =========================================================================

    /**
     * Baseline: single-threaded Sliding Window {@code tryAcquire()} throughput.
     */
    @Benchmark
    @Threads(1)
    public void slidingWindow_1Thread(Blackhole bh) {
        bh.consume(slidingWindow.tryAcquire());
    }

    /**
     * 4-thread Sliding Window throughput.
     */
    @Benchmark
    @Threads(4)
    public void slidingWindow_4Threads(Blackhole bh) {
        bh.consume(slidingWindow.tryAcquire());
    }

    /**
     * 8-thread Sliding Window throughput — primary resume benchmark number.
     */
    @Benchmark
    @Threads(8)
    public void slidingWindow_8Threads(Blackhole bh) {
        bh.consume(slidingWindow.tryAcquire());
    }

    /**
     * 16-thread Sliding Window throughput — high contention.
     */
    @Benchmark
    @Threads(16)
    public void slidingWindow_16Threads(Blackhole bh) {
        bh.consume(slidingWindow.tryAcquire());
    }

    // =========================================================================
    // Config / Factory overhead benchmarks
    // =========================================================================

    /**
     * Measures the cost of constructing a limiter via the factory.
     * Expected: very fast (simple object allocation).
     */
    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public RateLimiter factoryCreation_TokenBucket() {
        return RateLimiterFactory.tokenBucket(100, 10);
    }

    /**
     * Measures the cost of constructing a Sliding Window via the factory.
     */
    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public RateLimiter factoryCreation_SlidingWindow() {
        return RateLimiterFactory.slidingWindow(100, 1, TimeUnit.SECONDS);
    }
}
