# Java Rate Limiter Library

> **Production-grade Token Bucket & Sliding Window Counter rate limiting — lock-free, zero dependencies, benchmarked.**

[![Java 11+](https://img.shields.io/badge/Java-11%2B-blue.svg)](https://adoptium.net/)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.vikaskushwaha/rate-limiter.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.vikaskushwaha/rate-limiter)
[![Build Status](https://github.com/vikaskushwaha97/rate-limiter/actions/workflows/maven.yml/badge.svg)](https://github.com/vikaskushwaha97/rate-limiter/actions/workflows/maven.yml)
[![Coverage](https://img.shields.io/badge/Coverage-100%25-brightgreen.svg)]()
[![JUnit 5](https://img.shields.io/badge/Tests-JUnit%205-green.svg)](https://junit.org/junit5/)
[![JMH](https://img.shields.io/badge/Benchmarks-JMH-orange.svg)](https://github.com/openjdk/jmh)
[![MIT License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## Table of Contents
1. [Installation](#installation)
2. [Quick Start](#quick-start)
3. [Architecture](#architecture)
4. [Algorithms](#algorithms)
5. [API Reference](#api-reference)
6. [Design Decisions](#design-decisions)
7. [Benchmarks](#benchmarks)
8. [Running Tests](#running-tests)
9. [Project Structure](#project-structure)
10. [Contributing](#contributing)
11. [Changelog](#changelog)

---

## Installation

**Maven:**
```xml
<dependency>
    <groupId>io.github.vikaskushwaha</groupId>
    <artifactId>rate-limiter</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Gradle:**
```groovy
implementation 'io.github.vikaskushwaha:rate-limiter:1.0.0'
```

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

## Architecture

### Component Overview

```
┌─────────────────────────────────────────────────────────────┐
│                      Consumer Code                          │
│         if (limiter.tryAcquire()) { ... }                   │
└─────────────────────┬───────────────────────────────────────┘
                      │ depends on interface
┌─────────────────────▼───────────────────────────────────────┐
│               RateLimiter (interface)                        │
│   tryAcquire() · tryAcquire(n) · acquire() · getCurrentRate │
└────────┬─────────────────────────────────┬──────────────────┘
         │ implements                      │ implements
┌────────▼──────────────┐    ┌─────────────▼──────────────────┐
│ TokenBucketRateLimiter │    │ SlidingWindowRateLimiter       │
│                        │    │                                │
│ AtomicReference<State> │    │ AtomicLong[] circular buckets  │
│ Lock-free CAS pipeline │    │ Hot path: lock-free increment  │
│ Lazy refill on demand  │    │ Cold path: synchronized rotate │
└────────────────────────┘    └────────────────────────────────┘
         ▲                              ▲
         │ creates                      │ creates
┌────────┴──────────────────────────────┴──────────────────────┐
│                    RateLimiterFactory                         │
│   tokenBucket(capacity, rate) · slidingWindow(limit, window) │
│   tokenBucket(config)         · slidingWindow(config)        │
└──────────────────────────┬───────────────────────────────────┘
                           │ accepts
              ┌────────────▼─────────────┐
              │    RateLimiterConfig      │
              │    (Immutable + Builder)  │
              │    Fail-fast validation   │
              └──────────────────────────┘
```

### Design Patterns

| Pattern | Where | Purpose |
|---|---|---|
| **Strategy** | `RateLimiter` interface | Swap algorithms without call-site changes |
| **Static Factory Method** | `RateLimiterFactory` | Named constructors, interface return type, decouples callers from impls |
| **Builder** | `RateLimiterConfig.Builder` | Fluent configuration with fail-fast validation at build time |
| **Immutable Value Object** | `RateLimiterConfig`, `State` | Thread-safe by construction, no defensive copies needed |
| **CAS State Machine** | `TokenBucketRateLimiter.State` | Lock-free atomic transitions of (tokens + timestamp) in one CAS |

### Concurrency Model

**Token Bucket** — fully lock-free:
- All mutable state is packed into an immutable `State` record inside a single `AtomicReference`
- Refill computation and token consumption happen in one CAS loop — eliminates TOCTOU races
- Rate tracking uses a separate `AtomicReference<RateSnapshot>` — non-destructive reads

**Sliding Window** — hot/cold path separation:
- **Hot path** (99%+ of calls): CAS increment on `AtomicLong` bucket — always lock-free
- **Cold path** (once per sub-bucket period): `synchronized` rotation to clear stale slots and advance the index
- Accuracy bound: at most `limit + N - 1` grants under extreme concurrency (documented, acceptable for rate limiting)

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

### `RateLimitExceededException`

| Method | Description |
|---|---|
| `getRequestedPermits()` | Number of permits the caller tried to acquire |
| `getAvailablePermits()` | Approximate permits available at rejection time |

---

## Design Decisions

> This section exists to answer the single most common interviewer question: *"Walk me through your design decisions."*

### 1. Why `AtomicReference<State>` CAS instead of `synchronized`?

`synchronized` acquires a JVM monitor — it causes thread suspension/context-switching under contention, which is expensive. The Token Bucket packs both `tokens` and `lastRefillNanos` into an immutable `State` object held by a single `AtomicReference`. This allows the entire refill-and-consume operation to execute in one `compareAndSet()` call — no locks, no TOCTOU window. Under low-to-medium contention, CAS is 3–10× faster than a monitor. Under extreme contention, CAS retry loops can hurt — but rate limiters are fundamentally contention-reducing devices, so the load they see is inherently bounded.

### 2. Why lazy refill (no background thread)?

A background thread adds:
- **Lifecycle complexity** — who owns it? When does it shut down?
- **Scheduling jitter** — the JVM scheduler may not wake the thread precisely on time
- **Memory pressure** — each limiter instance would own a thread

Lazy refill computes "tokens owed" from `System.nanoTime()` delta at call time. It is mathematically equivalent to eager refill, requires zero threads, and is trivially correct.

### 3. Why a circular array for Sliding Window instead of a `ConcurrentLinkedQueue`?

A queue of timestamped events has O(requests) memory and O(requests) scan time. A circular array of `N` buckets has **O(N) fixed memory** and **O(1) bucket lookup** by modulo arithmetic — regardless of request volume. For production workloads (millions of requests), this is the only viable approach.

### 4. Why is Sliding Window rotation `synchronized` but Token Bucket is not?

Token Bucket merges refill and permit consumption into a single CAS pipeline using an immutable `State` object wrapped in an `AtomicReference` — this guarantees atomic updates to both time and tokens without locks. Sliding Window rotation must atomically: (a) advance `currentBucketIndex`, (b) clear multiple stale bucket slots, and (c) update their timestamps. This multi-step operation across an array cannot be cleanly expressed as a single CAS — `synchronized` is the correct tool for this **cold path** (fires at most once per sub-bucket period). The **hot path** (incrementing the current bucket) remains always lock-free.

### 5. Why `RateLimiterFactory` instead of exposing constructors?

- Named factory methods (`tokenBucket`, `slidingWindow`) communicate intent better than overloaded constructors
- They return the `RateLimiter` interface — callers are decoupled from the concrete class
- Future versions can add caching, wrappers, or decoration without any call-site changes
- Classic *Effective Java* Item 1: consider static factory methods over constructors

### 6. Why `RuntimeException` for `RateLimitExceededException`?

Rate limit violations are environmental, not business-recoverable conditions. Forcing `throws` declarations on every call site (like checked exceptions would) pollutes every API. Callers that want to handle it explicitly still can. This follows the same philosophy as `IllegalStateException` and Spring's `DataAccessException`. Additionally, the exception carries structured data (`requestedPermits`, `availablePermits`) so callers can build `Retry-After` headers without parsing the message string.

### 7. Why Java 11 minimum, not Java 8?

Java 8 is EOL. Java 11 is the oldest LTS still in active production use at most Indian product companies. `java.util.concurrent` APIs we use (`AtomicLong`, `AtomicReference`, `CountDownLatch`, `ExecutorService`) are all available in Java 8, but targeting 11 signals modernity and allows use of newer JVM features if needed.

### 8. Why immutable `State` object instead of separate `AtomicLong` fields?

The original design used two separate `AtomicLong` fields (`tokens`, `lastRefillTime`) — this created a "Phantom Empty" race condition where Thread A could read `tokens` and Thread B could update `lastRefillTime` between reads, causing inconsistent state. Packing both into a single immutable `State` object inside an `AtomicReference` guarantees that both values are always read and written together atomically — the CAS either succeeds or retries with a consistent snapshot.

---

## Benchmarks

See [BENCHMARK_RESULTS.md](rate-limiter/BENCHMARK_RESULTS.md) for full numbers.

**Token Bucket (JMH, 5 iterations × 1s measurement, JVM: OpenJDK 17):**

| Threads | Throughput | Avg Latency |
|---|---|---|
| 1 | ~22.0 M ops/sec | ~45 ns/op |
| 4 | ~26.5 M ops/sec | ~430 ns/op |
| 8 | ~22.7 M ops/sec | ~972 ns/op |
| 16 | ~8.8 M ops/sec | ~2198 ns/op |

**Sliding Window (JMH, 5 iterations × 1s measurement, JVM: OpenJDK 17):**

| Threads | Throughput | Avg Latency |
|---|---|---|
| 1 | ~18.6 M ops/sec | ~50 ns/op |
| 4 | ~17.0 M ops/sec | ~602 ns/op |
| 8 | ~14.8 M ops/sec | ~1115 ns/op |
| 16 | ~16.2 M ops/sec | ~2122 ns/op |

**Factory Creation Overhead:**

| Factory Method | Avg Time |
|---|---|
| `tokenBucket(100, 10)` | ~47 ns |
| `slidingWindow(100, 1s)` | ~91 ns |

**To run benchmarks:**

```bash
cd rate-limiter
mvn package -DskipTests
java -jar target/rate-limiter-1.0.0-benchmarks.jar \
     -wi 3 -i 5 -f 1 \
     -rf json -rff BENCHMARK_RESULTS.json
```

---

## Running Tests

```bash
cd rate-limiter

# Run all unit + stress tests (27 tests)
mvn test

# Run only stress tests
mvn test -Dtest=ConcurrencyStressTest

# Run only Token Bucket tests
mvn test -Dtest=TokenBucketRateLimiterTest

# Run only Sliding Window tests
mvn test -Dtest=SlidingWindowRateLimiterTest

# Generate Javadoc
mvn javadoc:javadoc
# Output: target/site/apidocs/index.html

# Full build (with JaCoCo coverage)
mvn clean verify -Dgpg.skip

# Run the demo app
cd ../rate-limiter-demo
mvn compile exec:java
```

**Expected output:**
```
[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## Project Structure

```
rate-limiter/                               ← Repository root
├── pom.xml                                 ← Parent POM (multi-module)
├── README.md                               ← This file
├── LICENSE                                 ← MIT License
│
├── rate-limiter/                           ← Core library module
│   ├── pom.xml                             ← Library POM (io.github.vikaskushwaha:rate-limiter:1.0.0)
│   ├── BENCHMARK_RESULTS.md               ← Full JMH benchmark data
│   ├── .github/workflows/maven.yml        ← GitHub Actions CI pipeline
│   └── src/
│       ├── main/java/io/github/vikaskushwaha/ratelimiter/
│       │   ├── package-info.java           ← Package-level Javadoc
│       │   ├── RateLimiter.java            ← Core interface (4 methods)
│       │   ├── RateLimiterConfig.java      ← Immutable config + Builder
│       │   ├── RateLimiterFactory.java     ← Static factory (entry point)
│       │   ├── TokenBucketRateLimiter.java ← Algorithm 1: Lock-free Token Bucket
│       │   ├── SlidingWindowRateLimiter.java ← Algorithm 2: Sliding Window Counter
│       │   └── RateLimitExceededException.java ← Structured unchecked exception
│       └── test/java/io/github/vikaskushwaha/ratelimiter/
│           ├── TokenBucketRateLimiterTest.java   ← 11 unit tests
│           ├── SlidingWindowRateLimiterTest.java ← 11 unit tests
│           ├── ConcurrencyStressTest.java        ← 5 stress tests (20 threads × 1000 calls)
│           └── RateLimiterBenchmark.java         ← 12 JMH benchmarks
│
└── rate-limiter-demo/                      ← Demo consumer module
    ├── pom.xml                             ← Depends on rate-limiter:1.0.0
    └── src/main/java/com/example/Main.java ← CLI demo showing library usage
```

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## Changelog

### v1.0.0
- Initial release
- Implemented Token Bucket algorithm with `AtomicReference<State>` CAS state machine
- Implemented Sliding Window Counter algorithm with circular `AtomicLong[]` array
- Static factory pattern with 6 overloaded creation methods
- Immutable `RateLimiterConfig` with fluent Builder and fail-fast validation
- Structured `RateLimitExceededException` with permit introspection
- 27 JUnit 5 unit and concurrency stress tests (20 threads × 1000 calls)
- 12 JMH benchmarks across 1/4/8/16 thread configurations
- JaCoCo code coverage integration
- GitHub Actions CI/CD pipeline
- Maven Central-ready POM (GPG signing, Javadoc JAR, Source JAR)

---

## License

MIT License — see [LICENSE](LICENSE).

---

*Built by Vikas Kushwaha | Java · JMH · JUnit 5 · Maven · AtomicReference · CAS · Lock-Free Concurrency*
