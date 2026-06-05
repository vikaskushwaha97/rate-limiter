package com.example;

import io.github.vikaskushwaha.ratelimiter.RateLimiter;
import io.github.vikaskushwaha.ratelimiter.RateLimiterFactory;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=========================================");
        System.out.println("🚀 Rate Limiter Library Local Demo 🚀");
        System.out.println("=========================================\n");

        // 1. Create a Rate Limiter using your Factory pattern
        // We configure a Token Bucket that allows 5 requests per second
        RateLimiter rateLimiter = RateLimiterFactory.tokenBucket(5, 5);
        System.out.println("Configured: Token Bucket (Burst: 5 req, Rate: 5 req/sec)");
        System.out.println("-----------------------------------------\n");

        // 2. Simulate rapid incoming requests
        System.out.println("Simulating 8 rapid, back-to-back requests...");
        for (int i = 1; i <= 8; i++) {
            boolean allowed = rateLimiter.tryAcquire();
            
            if (allowed) {
                System.out.println("Request " + i + ": SUCCESS ✅");
            } else {
                System.out.println("Request " + i + ": BLOCKED (Rate limit exceeded) ❌");
            }
        }

        System.out.println("\n-----------------------------------------");
        System.out.println("Waiting for 1.1 seconds for the bucket to refill...");
        Thread.sleep(1100); 

        // 3. Try another request after the refill period
        System.out.println("\nTrying another request after waiting:");
        boolean allowed = rateLimiter.tryAcquire();
        System.out.println("Request 9: " + (allowed ? "SUCCESS ✅" : "BLOCKED ❌"));
        System.out.println("\n=========================================");
        System.out.println("Demo finished successfully!");
    }
}
