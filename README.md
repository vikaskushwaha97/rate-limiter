# Java Rate Limiter Library

> **Production-grade Token Bucket & Sliding Window Counter rate limiting — lock-free, zero dependencies, benchmarked.**

[![Java 11+](https://img.shields.io/badge/Java-11%2B-blue.svg)](https://adoptium.net/)
[![JUnit 5](https://img.shields.io/badge/Tests-JUnit%205-green.svg)](https://junit.org/junit5/)
[![JMH](https://img.shields.io/badge/Benchmarks-JMH-orange.svg)](https://github.com/openjdk/jmh)
[![MIT License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## Table of Contents
1. [Quick Start](#quick-start)
2. [Algorithms](#algorithms)
3. [API Reference](#api-reference)
4. [Design Decisions](#design-decisions)
5. [Benchmarks](#benchmarks)
6. [Running Tests](#running-tests)
7. [Project Structure](#project-structure)

---

## Quick Start

**Two lines to integrate:**

```java
// Token Bucket: 100 capacity, refill 10 tokens/second
RateLimiter limiter = RateLimiterFactory.tokenBucket(100, 10);

if (limiter.tryAcquire()) {
    // handle request
} else {
    // reject — 429 Too Many Requests
}
```

**Using acquire() (throws on rejection):**

```java
try {
    limiter.acquire();
    // handle request
} catch (RateLimitExceededException e) {
    // respond with 429
}
```

**Sliding Window (60 requests/minute):**

```java
RateLimiter limiter = RateLimiterFactory.slidingWindow(60, 1, TimeUnit.MINUTES);
```

**Advanced config:**

```java
RateLimiterConfig config = RateLimiterConfig.builder()
    .capacity(500)
    .refillRate(50)
    .refillPeriod(1, TimeUnit.SECONDS)
    .windowDuration(1, TimeUnit.SECONDS)
    .windowBuckets(10)
    .build();

RateLimiter limiter = RateLimiterFactory.tokenBucket(config);
```

---

## Algorithms

### Token Bucket

```
capacity = 10 tokens
refill   = 2 tokens/second

t=0s  [██████████]  10 tokens — full
t=0s  request  →  [█████████ ]   9 tokens ✅
t=0s  request  →  [████████  ]   8 tokens ✅
...
t=0s  request  →  [          ]   0 tokens ✅ (10th)
t=0s  request  →  [          ]   DENIED ❌
t=1s  refill   →  [██        ]   2 tokens added
t=1s  request  →  [█         ]   1 token  ✅
```

**Key properties:**
- Allows bursting up to `capacity` requests
- Smooth refill — `N` tokens every `T` time period
- Lazy refill — computed on demand using `System.nanoTime()`, no background thread

### Sliding Window Counter

```
window = 1 second, 4 sub-buckets (250ms each), limit = 8

|--250ms--|--250ms--|--250ms--|--250ms--|
|   [3]   |   [2]   |   [2]   |   [1]   |   total = 8 → at limit
           ↑ now
```

**Key properties:**
- Divides the window into `N` sub-buckets (circular array)
- Requests are counted per sub-bucket; old buckets are cleared on rotation
- Smoother than fixed window — no "double-spike" at window boundary

---

## API Reference

### `RateLimiter` (interface)

| Method | Description |
|---|---|
| `boolean tryAcquire()` | Try to acquire 1 permit. Returns `true` if granted. Non-blocking. |
| `boolean tryAcquire(int permits)` | Try to acquire N permits atomically. Either all granted or none. |
| `void acquire()` | Acquire 1 permit, throwing `RateLimitExceededException` if denied. |
| `double getCurrentRate()` | Approximate current rate in requests/second. |

### `RateLimiterFactory` (static factory)

| Method | Description |
|---|---|
| `tokenBucket(capacity, refillRate)` | Token Bucket, refills `refillRate` tokens/second |
| `tokenBucket(capacity, rate, period, unit)` | Token Bucket with custom refill period |
| `tokenBucket(config)` | Token Bucket from `RateLimiterConfig` |
| `slidingWindow(limit, duration, unit)` | Sliding Window with 10 sub-buckets |
| `slidingWindow(limit, duration, unit, buckets)` | Sliding Window with custom bucket count |
| `slidingWindow(config)` | Sliding Window from `RateLimiterConfig` |

### `RateLimiterConfig.Builder`

```java
RateLimiterConfig.builder()
    .capacity(100)                          // max tokens / window limit
    .refillRate(10)                         // tokens added per period (Token Bucket)
    .refillPeriod(1, TimeUnit.SECONDS)      // period for refill (Token Bucket)
    .windowDuration(1, TimeUnit.SECONDS)    // total window size (Sliding Window)
    .windowBuckets(10)                      // sub-bucket count (Sliding Window)
    .build();
```

---

## Design Decisions

> This section exists to answer the single most common interviewer question: *"Walk me through your design decisions."*

### 1. Why `AtomicLong` CAS instead of `synchronized`?

`synchronized` acquires a JVM monitor — it causes thread suspension/context-switching under contention, which is expensive. `AtomicLong.compareAndSet()` is implemented as a single CPU instruction (`LOCK CMPXCHG` on x86). Under low-to-medium contention, CAS is 3–10× faster than a monitor. Under extreme contention, CAS retry loops can hurt — but rate limiters are fundamentally contention-reducing devices, so the load they see is inherently bounded.

### 2. Why lazy refill (no background thread)?

A background thread adds:
- **Lifecycle complexity** — who owns it? When does it shut down?
- **Scheduling jitter** — the JVM scheduler may not wake the thread precisely on time
- **Memory pressure** — each limiter instance would own a thread

Lazy refill computes "tokens owed" from `System.nanoTime()` delta at call time. It is mathematically equivalent to eager refill, requires zero threads, and is trivially correct.

### 3. Why a circular array for Sliding Window instead of a `ConcurrentLinkedQueue`?

A queue of timestamped events has O(requests) memory and O(requests) scan time. A circular array of `N` buckets has **O(N) fixed memory** and **O(1) bucket lookup** by modulo arithmetic — regardless of request volume. For production workloads (millions of requests), this is the only viable approach.

### 4. Why is Sliding Window rotation `synchronized` but Token Bucket is not?

Token Bucket refill uses CAS on two independent `AtomicLong` fields — the state machine allows losers to safely re-read and retry. Sliding Window rotation must atomically: (a) advance `currentBucketIndex`, (b) clear multiple stale bucket slots, and (c) update their timestamps. This multi-step operation cannot be expressed as a single CAS — `synchronized` is the correct tool for this **cold path** (fires at most once per sub-bucket period). The **hot path** (incrementing the current bucket) remains always lock-free.

### 5. Why `RateLimiterFactory` instead of exposing constructors?

- Named factory methods (`tokenBucket`, `slidingWindow`) communicate intent better than overloaded constructors
- They return the `RateLimiter` interface — callers are decoupled from the concrete class
- Future versions can add caching, wrappers, or decoration without any call-site changes
- Classic *Effective Java* Item 1: consider static factory methods over constructors

### 6. Why `RuntimeException` for `RateLimitExceededException`?

Rate limit violations are environmental, not business-recoverable conditions. Forcing `throws` declarations on every call site (like checked exceptions would) pollutes every API. Callers that want to handle it explicitly still can. This follows the same philosophy as `IllegalStateException` and Spring's `DataAccessException`.

### 7. Why Java 11 minimum, not Java 8?

Java 8 is EOL. Java 11 is the oldest LTS still in active production use at most Indian product companies. `java.util.concurrent` APIs we use (`AtomicLong`, `CountDownLatch`, `ExecutorService`) are all available in Java 8, but targeting 11 signals modernity and allows use of newer JVM features if needed.

---

## Benchmarks

See [BENCHMARK_RESULTS.md](BENCHMARK_RESULTS.md) for full numbers.

**Summary (Token Bucket, JMH, 5 iterations × 1s measurement, JVM: OpenJDK 11):**

| Threads | Throughput | Avg Latency |
|---|---|---|
| 1 | ~X M ops/sec | ~Y ns/op |
| 4 | ~X M ops/sec | ~Y ns/op |
| 8 | ~X M ops/sec | ~Y ns/op |
| 16 | ~X M ops/sec | ~Y ns/op |

> Replace X/Y with your actual JMH numbers after running benchmarks.

**To run benchmarks:**

```bash
mvn package -DskipTests
java -jar target/rate-limiter-1.0.0-benchmarks.jar \
     -wi 3 -i 5 -f 1 \
     -rf json -rff BENCHMARK_RESULTS.json
```

---

## Running Tests

```bash
# Run all unit + stress tests
mvn test

# Run only stress tests
mvn test -Dtest=ConcurrencyStressTest

# Run only Token Bucket tests
mvn test -Dtest=TokenBucketRateLimiterTest

# Generate Javadoc
mvn javadoc:javadoc
# Output: target/site/apidocs/index.html

# Full build
mvn clean install
```

**Expected output:**
```
[INFO] Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## Project Structure

```
rate-limiter/
├── pom.xml
├── README.md
├── BENCHMARK_RESULTS.md
└── src/
    ├── main/java/io/github/vikaskushwaha/ratelimiter/
    │   ├── package-info.java
    │   ├── RateLimiter.java                  ← Core interface
    │   ├── RateLimiterConfig.java            ← Immutable config + Builder
    │   ├── RateLimiterFactory.java           ← Static factory (entry point)
    │   ├── TokenBucketRateLimiter.java       ← Algorithm 1: Token Bucket
    │   ├── SlidingWindowRateLimiter.java     ← Algorithm 2: Sliding Window
    │   └── RateLimitExceededException.java   ← Unchecked exception
    └── test/java/io/github/vikaskushwaha/ratelimiter/
        ├── TokenBucketRateLimiterTest.java   ← Unit tests
        ├── SlidingWindowRateLimiterTest.java ← Unit tests
        ├── ConcurrencyStressTest.java        ← 20 threads × 1000 calls
        └── RateLimiterBenchmark.java         ← JMH benchmarks
```

---

## License

MIT License — see [LICENSE](LICENSE).

---

*Built by Vikas Kushwaha | Java · JMH · JUnit 5 · Maven · AtomicLong · CAS*
