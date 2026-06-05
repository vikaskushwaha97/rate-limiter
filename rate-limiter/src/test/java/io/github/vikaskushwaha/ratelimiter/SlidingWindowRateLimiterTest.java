package io.github.vikaskushwaha.ratelimiter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SlidingWindowRateLimiter}.
 *
 * <p>Covers happy path, edge cases, boundary conditions, rotation logic,
 * and exception-throwing behaviour.
 */
@DisplayName("SlidingWindowRateLimiter")
class SlidingWindowRateLimiterTest {

    private SlidingWindowRateLimiter limiter;

    @BeforeEach
    void setUp() {
        // Allow 10 requests per second, 10 sub-buckets (100ms each)
        limiter = new SlidingWindowRateLimiter(10, 1, TimeUnit.SECONDS, 10);
    }

    // =========================================================================
    // Happy path
    // =========================================================================

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("First N tryAcquire() calls within limit all succeed")
        void firstNRequestsSucceed() {
            for (int i = 0; i < 10; i++) {
                assertTrue(limiter.tryAcquire(), "Expected grant on call " + (i + 1));
            }
        }

        @Test
        @DisplayName("tryAcquire(int) grants multiple permits")
        void tryAcquireMultiplePermits() {
            assertTrue(limiter.tryAcquire(5));
            // 5 of 10 used
            assertTrue(limiter.tryAcquire(5));
        }

        @Test
        @DisplayName("acquire() succeeds when under limit")
        void acquireSucceedsUnderLimit() {
            assertDoesNotThrow(() -> limiter.acquire());
        }

        @Test
        @DisplayName("getCurrentWindowCount() reflects granted requests")
        void getCurrentWindowCountReflectsRequests() {
            limiter.tryAcquire();
            limiter.tryAcquire();
            assertEquals(2, limiter.getCurrentWindowCount());
        }
    }

    // =========================================================================
    // Boundary conditions
    // =========================================================================

    @Nested
    @DisplayName("Boundary conditions")
    class BoundaryConditions {

        @Test
        @DisplayName("Exactly at limit — last request granted, next rejected")
        void exactlyAtLimitLastGrantedNextRejected() {
            for (int i = 0; i < 10; i++) limiter.tryAcquire();
            assertFalse(limiter.tryAcquire(), "Request beyond limit must be rejected");
        }

        @Test
        @DisplayName("acquire() throws RateLimitExceededException when limit reached")
        void acquireThrowsWhenLimitReached() {
            for (int i = 0; i < 10; i++) limiter.tryAcquire();
            assertThrows(RateLimitExceededException.class, () -> limiter.acquire());
        }

        @Test
        @DisplayName("tryAcquire(permits > limit) always returns false")
        void tryAcquireMoreThanLimitReturnsFalse() {
            assertFalse(limiter.tryAcquire(11));
        }

        @Test
        @DisplayName("tryAcquire(0) throws IllegalArgumentException")
        void tryAcquireZeroThrows() {
            assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(0));
        }

        @Test
        @DisplayName("tryAcquire(-1) throws IllegalArgumentException")
        void tryAcquireNegativeThrows() {
            assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(-1));
        }

        @Test
        @DisplayName("Window resets after full window elapses")
        void windowResetsAfterElapsed() throws InterruptedException {
            // Exhaust the limit
            for (int i = 0; i < 10; i++) limiter.tryAcquire();
            assertFalse(limiter.tryAcquire());

            // Wait for the full window (1 second + small buffer)
            Thread.sleep(1100);

            // Should be able to request again
            assertTrue(limiter.tryAcquire(), "Should be granted after window reset");
        }
    }

    // =========================================================================
    // Edge cases
    // =========================================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Limit of 1 — only one request per window")
        void limitOneAllowsExactlyOne() {
            SlidingWindowRateLimiter tiny =
                new SlidingWindowRateLimiter(1, 1, TimeUnit.SECONDS, 1);
            assertTrue(tiny.tryAcquire());
            assertFalse(tiny.tryAcquire());
        }

        @Test
        @DisplayName("Single bucket (buckets=1) behaves as fixed window")
        void singleBucketBehavesAsFixedWindow() {
            SlidingWindowRateLimiter sw =
                new SlidingWindowRateLimiter(5, 1, TimeUnit.SECONDS, 1);
            for (int i = 0; i < 5; i++) assertTrue(sw.tryAcquire());
            assertFalse(sw.tryAcquire());
        }

        @Test
        @DisplayName("Factory method slidingWindow() creates a working limiter")
        void factoryMethodWorks() {
            RateLimiter rl = RateLimiterFactory.slidingWindow(5, 1, TimeUnit.SECONDS);
            assertTrue(rl.tryAcquire());
        }

        @Test
        @DisplayName("getCurrentRate() returns non-negative value")
        void getCurrentRateIsNonNegative() {
            limiter.tryAcquire();
            assertTrue(limiter.getCurrentRate() >= 0.0);
        }

        @Test
        @DisplayName("Config builder windowBuckets < 1 throws")
        void configValidationWindowBucketsZero() {
            assertThrows(IllegalArgumentException.class, () ->
                RateLimiterConfig.builder()
                    .capacity(10)
                    .windowBuckets(0)
                    .build());
        }
    }

    // =========================================================================
    // toString / diagnostics
    // =========================================================================

    @Test
    @DisplayName("toString() returns a non-empty string")
    void toStringReturnsNonEmpty() {
        assertFalse(limiter.toString().isEmpty());
    }

    @Test
    @DisplayName("getLimit() returns the configured limit")
    void getLimitReturnsConfiguredValue() {
        assertEquals(10, limiter.getLimit());
    }
}
