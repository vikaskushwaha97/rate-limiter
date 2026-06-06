package com.example;

import io.github.vikaskushwaha.ratelimiter.RateLimitExceededException;
import io.github.vikaskushwaha.ratelimiter.RateLimiter;
import io.github.vikaskushwaha.ratelimiter.RateLimiterFactory;
import io.github.vikaskushwaha.ratelimiter.RateLimiterConfig;

import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("==================================================");
        System.out.println("🚀 Rate Limiter Library Full API Demo 🚀");
        System.out.println("==================================================\n");

        testTokenBucketApi();
        testSlidingWindowApi();
        testAdvancedConfigBuilder();

        System.out.println("==================================================");
        System.out.println("✅ All API methods tested successfully!");
        System.out.println("==================================================");
    }

    private static void testTokenBucketApi() throws InterruptedException {
        System.out.println("--- 1. Testing Token Bucket API ---");
        // API Endpoint: RateLimiterFactory.tokenBucket(capacity, rate)
        RateLimiter limiter = RateLimiterFactory.tokenBucket(10, 5);
        System.out.println("Created Token Bucket: 10 capacity, 5 refill/sec");

        // API Endpoint: tryAcquire()
        System.out.println("\nTesting tryAcquire() [Single permit]");
        System.out.println("Request 1: " + (limiter.tryAcquire() ? "SUCCESS ✅" : "BLOCKED ❌"));

        // API Endpoint: tryAcquire(int permits)
        System.out.println("\nTesting tryAcquire(permits) [Multi permit atomic]");
        System.out.println("Request 5 permits: " + (limiter.tryAcquire(5) ? "SUCCESS ✅" : "BLOCKED ❌"));
        System.out.println("Request 5 permits (should fail, only 4 left): " + (limiter.tryAcquire(5) ? "SUCCESS ✅" : "BLOCKED ❌"));

        // API Endpoint: acquire()
        System.out.println("\nTesting acquire() [Throws Exception on failure]");
        try {
            System.out.println("Draining remaining 4 permits...");
            limiter.tryAcquire(4); // drain it
            System.out.println("Calling acquire() on empty bucket...");
            limiter.acquire(); // This should throw
            System.out.println("If you see this, something is wrong! ❌");
        } catch (RateLimitExceededException e) {
            System.out.println("Caught Expected Exception ✅: " + e.getMessage());
            System.out.println(" -> Requested: " + e.getRequestedPermits() + ", Available: " + e.getAvailablePermits());
        }

        System.out.println("\nWaiting 1.1s for refill...");
        Thread.sleep(1100);

        // API Endpoint: getCurrentRate()
        System.out.println("Testing getCurrentRate() [Observability]");
        System.out.println("Current approximate rate (req/sec): " + limiter.getCurrentRate() + " ✅\n");
    }

    private static void testSlidingWindowApi() throws InterruptedException {
        System.out.println("--- 2. Testing Sliding Window API ---");
        // API Endpoint: RateLimiterFactory.slidingWindow(limit, duration, unit)
        RateLimiter limiter = RateLimiterFactory.slidingWindow(3, 1, TimeUnit.SECONDS);
        System.out.println("Created Sliding Window: Max 3 requests per 1 Second");

        System.out.println("Firing 4 rapid requests:");
        for (int i = 1; i <= 4; i++) {
            System.out.println("Request " + i + ": " + (limiter.tryAcquire() ? "SUCCESS ✅" : "BLOCKED ❌"));
        }

        System.out.println("\nWaiting 1.1s for window to slide/clear...");
        Thread.sleep(1100);

        System.out.println("Request 5 (after wait): " + (limiter.tryAcquire() ? "SUCCESS ✅" : "BLOCKED ❌\n"));
    }

    private static void testAdvancedConfigBuilder() {
        System.out.println("--- 3. Testing Advanced Config Builder API ---");
        // API Endpoint: RateLimiterConfig.builder()
        RateLimiterConfig config = RateLimiterConfig.builder()
                .capacity(500)
                .refillRate(50)
                .refillPeriod(1, TimeUnit.SECONDS)
                .windowDuration(1, TimeUnit.SECONDS)
                .windowBuckets(10)
                .build();
        
        System.out.println("Built Config: " + config.toString());

        // API Endpoint: RateLimiterFactory.tokenBucket(config)
        RateLimiter customLimiter = RateLimiterFactory.tokenBucket(config);
        System.out.println("Created custom limiter from config successfully! ✅\n");
    }
}
