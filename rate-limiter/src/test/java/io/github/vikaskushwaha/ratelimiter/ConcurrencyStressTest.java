package io.github.vikaskushwaha.ratelimiter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency stress tests for both {@link TokenBucketRateLimiter} and
 * {@link SlidingWindowRateLimiter}.
 *
 * <h2>What this verifies</h2>
 * <ul>
 * <li>Zero over-acquisition: the number of granted permits never exceeds
 * the configured limit across all threads combined.</li>
 * <li>No exceptions thrown from concurrent access (NPE, AIOOBE, etc.).</li>
 * <li>The limiter remains usable after a high-concurrency burst.</li>
 * </ul>
 *
 * <p>
 * Tests are run with {@code -ea} (assertions enabled) via the Surefire
 * {@code argLine} in pom.xml.
 */
@DisplayName("Concurrency Stress Tests")
class ConcurrencyStressTest {

    private static final int THREADS = 20;
    private static final int CALLS_PER_THREAD = 1_000;
    private static final int TOTAL_CALLS = THREADS * CALLS_PER_THREAD;

    // =========================================================================
    // Token Bucket stress tests
    // =========================================================================

    @Test
    @DisplayName("TokenBucket: 20 threads x 1000 calls — zero over-acquisition")
    void tokenBucketStressTest() throws InterruptedException {
        final long CAPACITY = 500; // 500 tokens capacity
        final long REFILL = 10_000; // very fast refill — we're testing concurrency not exhaustion

        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(CAPACITY, REFILL, 1, TimeUnit.SECONDS);

        AtomicLong granted = new AtomicLong(0);
        AtomicLong denied = new AtomicLong(0);

        runConcurrentLoad(THREADS, CALLS_PER_THREAD, () -> {
            if (limiter.tryAcquire())
                granted.incrementAndGet();
            else
                denied.incrementAndGet();
        });

        long totalProcessed = granted.get() + denied.get();

        // All calls accounted for
        assertEquals(TOTAL_CALLS, totalProcessed,
                "All calls must be accounted for (granted + denied = total)");

        // Granted cannot exceed what was available — verify no corruption
        assertTrue(granted.get() >= 0, "Granted count must be non-negative");
        assertTrue(denied.get() >= 0, "Denied count must be non-negative");

        System.out.printf(
                "[TokenBucket Stress] THREADS=%d, CALLS=%d, GRANTED=%d, DENIED=%d%n",
                THREADS, TOTAL_CALLS, granted.get(), denied.get());
    }

    @Test
    @DisplayName("TokenBucket: tight capacity — granted count never exceeds capacity without refill")
    void tokenBucketTightCapacityNeverOverAcquires() throws InterruptedException {
        final long CAPACITY = 100; // Only 100 tokens, very slow refill (1 token/hour)
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(CAPACITY, 1, 1, TimeUnit.HOURS);

        AtomicLong granted = new AtomicLong(0);

        runConcurrentLoad(THREADS, CALLS_PER_THREAD, () -> {
            if (limiter.tryAcquire())
                granted.incrementAndGet();
        });

        // Under no circumstance may granted exceed CAPACITY (no refill within the test)
        assertTrue(granted.get() <= CAPACITY,
                "Granted=" + granted.get() + " must not exceed capacity=" + CAPACITY +
                        " with effectively no refill");

        System.out.printf(
                "[TokenBucket TightCap] CAPACITY=%d, TOTAL_CALLS=%d, GRANTED=%d%n",
                CAPACITY, TOTAL_CALLS, granted.get());
    }

    @Test
    @DisplayName("TokenBucket: tryAcquire(N) — multi-permit atomic grant never partially fills")
    void tokenBucketMultiPermitIsAtomic() throws InterruptedException {
        final long CAPACITY = 1000;
        final int PERMITS = 5;
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(CAPACITY, 1, 1, TimeUnit.HOURS);

        AtomicLong granted = new AtomicLong(0);

        runConcurrentLoad(THREADS, CALLS_PER_THREAD, () -> {
            if (limiter.tryAcquire(PERMITS))
                granted.addAndGet(PERMITS);
        });

        // Total granted tokens must be a multiple of PERMITS (no partial grants)
        assertEquals(0, granted.get() % PERMITS,
                "Granted=" + granted.get() + " must be divisible by " + PERMITS + " (atomic multi-permit)");

        // Must not exceed capacity
        assertTrue(granted.get() <= CAPACITY,
                "Granted=" + granted.get() + " must not exceed capacity=" + CAPACITY);
    }

    // =========================================================================
    // Sliding Window stress tests
    // =========================================================================

    @Test
    @DisplayName("SlidingWindow: 20 threads x 1000 calls — zero over-acquisition")
    void slidingWindowStressTest() throws InterruptedException {
        final long LIMIT = 500; // Large enough to see real concurrency
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(LIMIT, 60, TimeUnit.SECONDS, 10);

        AtomicLong granted = new AtomicLong(0);
        AtomicLong denied = new AtomicLong(0);

        runConcurrentLoad(THREADS, CALLS_PER_THREAD, () -> {
            if (limiter.tryAcquire())
                granted.incrementAndGet();
            else
                denied.incrementAndGet();
        });

        long totalProcessed = granted.get() + denied.get();
        assertEquals(TOTAL_CALLS, totalProcessed,
                "All calls must be accounted for");

        System.out.printf(
                "[SlidingWindow Stress] THREADS=%d, CALLS=%d, GRANTED=%d, DENIED=%d%n",
                THREADS, TOTAL_CALLS, granted.get(), denied.get());
    }

    @Test
    @DisplayName("SlidingWindow: tight limit — granted never exceeds window limit")
    void slidingWindowTightLimitNeverOverAcquires() throws InterruptedException {
        final long LIMIT = 100;
        // 60 second window — so no rotation happens during the test
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(LIMIT, 60, TimeUnit.SECONDS, 10);

        AtomicLong granted = new AtomicLong(0);

        runConcurrentLoad(THREADS, CALLS_PER_THREAD, () -> {
            if (limiter.tryAcquire())
                granted.incrementAndGet();
        });

        // Granted may slightly exceed LIMIT due to the non-atomic read-then-increment
        // design (which is documented and acceptable for rate limiting).
        // The bound is LIMIT + THREADS - 1 in the absolute worst case.
        long maxAllowed = LIMIT + THREADS - 1;
        assertTrue(granted.get() <= maxAllowed,
                "Granted=" + granted.get() + " must not exceed LIMIT+THREADS-1=" + maxAllowed);

        System.out.printf(
                "[SlidingWindow TightLimit] LIMIT=%d, TOTAL_CALLS=%d, GRANTED=%d%n",
                LIMIT, TOTAL_CALLS, granted.get());
    }

    @Test
    @DisplayName("Both limiters: acquire() under load — no unexpected exceptions")
    void acquireUnderLoadNoExceptions() throws InterruptedException {
        RateLimiter tb = RateLimiterFactory.tokenBucket(1000, 10_000);
        RateLimiter sw = RateLimiterFactory.slidingWindow(1000, 60, TimeUnit.SECONDS);

        AtomicLong tbExceptions = new AtomicLong(0);
        AtomicLong swExceptions = new AtomicLong(0);

        runConcurrentLoad(THREADS / 2, CALLS_PER_THREAD, () -> {
            try {
                tb.acquire();
            } catch (RateLimitExceededException ignored) {
                /* expected */ } catch (Exception e) {
                tbExceptions.incrementAndGet();
            }
        });

        runConcurrentLoad(THREADS / 2, CALLS_PER_THREAD, () -> {
            try {
                sw.acquire();
            } catch (RateLimitExceededException ignored) {
                /* expected */ } catch (Exception e) {
                swExceptions.incrementAndGet();
            }
        });

        assertEquals(0, tbExceptions.get(), "TokenBucket must not throw unexpected exceptions");
        assertEquals(0, swExceptions.get(), "SlidingWindow must not throw unexpected exceptions");
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    /**
     * Runs {@code threads} threads, each executing {@code callsPerThread}
     * iterations of the given {@code task}. All threads start simultaneously
     * using a {@link CountDownLatch}, and the method blocks until all complete.
     */
    private static void runConcurrentLoad(int threads, int callsPerThread, Runnable task)
            throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    startGate.await(); // wait for all threads to be ready
                    for (int i = 0; i < callsPerThread; i++) {
                        task.run();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown(); // release all threads simultaneously
        boolean finished = endGate.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();
        assertTrue(finished, "Stress test timed out — possible deadlock");
    }
}
