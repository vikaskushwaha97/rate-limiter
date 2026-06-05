package io.github.vikaskushwaha.ratelimiter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TokenBucketRateLimiter}.
 *
 * <p>Covers happy path, edge cases, boundary conditions, and
 * exception-throwing behaviour.
 */
@DisplayName("TokenBucketRateLimiter")
class TokenBucketRateLimiterTest {

    private TokenBucketRateLimiter limiter;

    @BeforeEach
    void setUp() {
        // 10 capacity, refill 10 tokens/second
        limiter = new TokenBucketRateLimiter(10, 10, 1, TimeUnit.SECONDS);
    }

    // =========================================================================
    // Happy path
    // =========================================================================

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("New bucket starts full — first N tryAcquire() calls succeed")
        void newBucketStartsFull() {
            for (int i = 0; i < 10; i++) {
                assertTrue(limiter.tryAcquire(), "Expected grant on call " + (i + 1));
            }
        }

        @Test
        @DisplayName("tryAcquire(int) grants multiple permits at once")
        void tryAcquireMultiplePermits() {
            assertTrue(limiter.tryAcquire(5));
            assertEquals(5, limiter.getAvailableTokens());
        }

        @Test
        @DisplayName("acquire() succeeds when tokens are available")
        void acquireSucceedsWhenTokensAvailable() {
            assertDoesNotThrow(() -> limiter.acquire());
        }

        @Test
        @DisplayName("Exact capacity requests all granted")
        void exactCapacityAllGranted() {
            for (int i = 0; i < 10; i++) {
                assertTrue(limiter.tryAcquire());
            }
            assertEquals(0, limiter.getAvailableTokens());
        }
    }

    // =========================================================================
    // Boundary conditions
    // =========================================================================

    @Nested
    @DisplayName("Boundary conditions")
    class BoundaryConditions {

        @Test
        @DisplayName("tryAcquire returns false when bucket is empty")
        void tryAcquireReturnsFalseWhenEmpty() {
            // Drain the bucket
            for (int i = 0; i < 10; i++) limiter.tryAcquire();
            assertFalse(limiter.tryAcquire());
        }

        @Test
        @DisplayName("acquire() throws RateLimitExceededException when empty")
        void acquireThrowsWhenEmpty() {
            for (int i = 0; i < 10; i++) limiter.tryAcquire();
            assertThrows(RateLimitExceededException.class, () -> limiter.acquire());
        }

        @Test
        @DisplayName("tryAcquire(permits > capacity) always returns false")
        void tryAcquireMoreThanCapacityReturnsFalse() {
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
        @DisplayName("Available tokens never exceed capacity")
        void availableTokensNeverExceedCapacity() {
            // Acquire one, then wait long enough to refill more than capacity
            limiter.tryAcquire();
            // Force time passage by sleeping slightly over one period
            try { Thread.sleep(1100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            // getAvailableTokens() triggers refill internally
            assertTrue(limiter.getAvailableTokens() <= limiter.getCapacity());
        }
    }

    // =========================================================================
    // Edge cases
    // =========================================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Capacity of 1 — only one concurrent request passes")
        void capacityOneAllowsExactlyOne() {
            TokenBucketRateLimiter tiny = new TokenBucketRateLimiter(1, 1, 1, TimeUnit.SECONDS);
            assertTrue(tiny.tryAcquire());
            assertFalse(tiny.tryAcquire());
        }

        @Test
        @DisplayName("Very large capacity — does not overflow AtomicLong")
        void veryLargeCapacity() {
            TokenBucketRateLimiter large = new TokenBucketRateLimiter(Long.MAX_VALUE / 2, 1, 1, TimeUnit.SECONDS);
            assertTrue(large.tryAcquire());
        }

        @Test
        @DisplayName("Builder config produces same behaviour as direct constructor")
        void builderAndConstructorEquivalent() {
            RateLimiterConfig config = RateLimiterConfig.builder()
                .capacity(10).refillRate(10).refillPeriod(1, TimeUnit.SECONDS).build();
            TokenBucketRateLimiter fromConfig = new TokenBucketRateLimiter(config);
            assertEquals(10, fromConfig.getAvailableTokens());
        }

        @Test
        @DisplayName("Factory method tokenBucket(capacity, rate) creates a working limiter")
        void factoryMethodWorks() {
            RateLimiter rl = RateLimiterFactory.tokenBucket(5, 5);
            assertTrue(rl.tryAcquire());
        }

        @Test
        @DisplayName("getCurrentRate() returns non-negative value")
        void getCurrentRateIsNonNegative() {
            limiter.tryAcquire();
            assertTrue(limiter.getCurrentRate() >= 0.0);
        }

        @Test
        @DisplayName("RateLimiterConfig validation — capacity <= 0 throws")
        void configValidationCapacityZero() {
            assertThrows(IllegalArgumentException.class, () ->
                RateLimiterConfig.builder().capacity(0).build());
        }

        @Test
        @DisplayName("RateLimiterConfig validation — refillRate <= 0 throws")
        void configValidationRefillRateZero() {
            assertThrows(IllegalArgumentException.class, () ->
                RateLimiterConfig.builder().capacity(10).refillRate(0).build());
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
}
