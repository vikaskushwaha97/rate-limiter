# Benchmark Results

> Run after `mvn package -DskipTests` using:
> ```bash
> mvn test-compile exec:java \
>     "-Dexec.classpathScope=test" \
>     "-Dexec.mainClass=org.openjdk.jmh.Main" \
>     "-Dexec.args=-wi 1 -i 2 -f 0 -rf json -rff BENCHMARK_RESULTS.json"
> ```

## Environment

| Property | Value |
|---|---|
| JVM | OpenJDK 17.0.16+8 (Eclipse Adoptium) |
| CPU | AMD Ryzen 7 5800HS with Radeon Graphics (8C/16T) |
| OS | Microsoft Windows 11 Home |
| JMH version | 1.37 |
| Warmup | 1 iteration × 1s |
| Measurement | 2 iterations × 1s |
| Forks | 0 (in-process, non-forked) |

> **Note:** Non-forked runs (`-f 0`) were used due to classpath limitations with the
> Maven exec plugin. For production-grade numbers, use the shaded benchmark JAR with `-f 1`.

---

## Token Bucket — Throughput (ops/ms)

| Benchmark | Threads | Score | Units |
|---|---|---|---|
| tokenBucket_1Thread | 1 | 22,050 | ops/ms |
| tokenBucket_4Threads | 4 | 26,550 | ops/ms |
| tokenBucket_8Threads | 8 | 22,776 | ops/ms |
| tokenBucket_16Threads | 16 | 8,848 | ops/ms |

## Token Bucket — Average Time (ns/op)

| Benchmark | Threads | Score | Units |
|---|---|---|---|
| tokenBucket_1Thread | 1 | 44.7 | ns/op |
| tokenBucket_4Threads | 4 | 430.0 | ns/op |
| tokenBucket_8Threads | 8 | 972.0 | ns/op |
| tokenBucket_16Threads | 16 | 2,198 | ns/op |

---

## Sliding Window — Throughput (ops/ms)

| Benchmark | Threads | Score | Units |
|---|---|---|---|
| slidingWindow_1Thread | 1 | 18,592 | ops/ms |
| slidingWindow_4Threads | 4 | 17,001 | ops/ms |
| slidingWindow_8Threads | 8 | 14,843 | ops/ms |
| slidingWindow_16Threads | 16 | 16,167 | ops/ms |

## Sliding Window — Average Time (ns/op)

| Benchmark | Threads | Score | Units |
|---|---|---|---|
| slidingWindow_1Thread | 1 | 49.8 | ns/op |
| slidingWindow_4Threads | 4 | 601.9 | ns/op |
| slidingWindow_8Threads | 8 | 1,115 | ns/op |
| slidingWindow_16Threads | 16 | 2,122 | ns/op |

---

## Factory Creation Overhead (ns/op)

| Benchmark | Score | Units |
|---|---|---|
| factoryCreation_TokenBucket | 46.6 | ns/op |
| factoryCreation_SlidingWindow | 90.9 | ns/op |

---

## Analysis

### Token Bucket
- **Peak throughput**: ~26.5M ops/sec at 4 threads — CAS contention is minimal here.
- **Single-threaded**: ~22M ops/sec (~44 ns/op) — this is the CAS + `System.nanoTime()` overhead baseline.
- **16-thread drop-off**: Throughput drops to ~8.8M ops/sec due to CAS retry contention on the shared `AtomicLong`. This is expected and acceptable — a rate limiter under 16 threads of contention is still servicing 8.8 million decisions per second.

### Sliding Window
- **Flatter scaling**: Throughput stays between 14.8–18.6M ops/sec across all thread counts. The hot path (`AtomicLong.addAndGet` on the current bucket) has less contention than Token Bucket because each sub-bucket is an independent `AtomicLong`.
- **Rotation overhead**: The `synchronized` rotation block fires at most once per sub-bucket period (100ms for 10 buckets/second), so it does not appear in these measurements.

### Factory Creation
- Token Bucket creation: ~47 ns — just object allocation + `System.nanoTime()`.
- Sliding Window creation: ~91 ns — slightly more due to the `AtomicLong[]` array allocation.

---

## Raw JMH Output

```
Benchmark                                              Mode  Cnt      Score   Error   Units
RateLimiterBenchmark.slidingWindow_16Threads           thrpt    2  16167.424           ops/ms
RateLimiterBenchmark.slidingWindow_1Thread             thrpt    2  18592.128           ops/ms
RateLimiterBenchmark.slidingWindow_4Threads            thrpt    2  17000.687           ops/ms
RateLimiterBenchmark.slidingWindow_8Threads            thrpt    2  14843.053           ops/ms
RateLimiterBenchmark.tokenBucket_16Threads             thrpt    2   8847.620           ops/ms
RateLimiterBenchmark.tokenBucket_1Thread               thrpt    2  22050.214           ops/ms
RateLimiterBenchmark.tokenBucket_4Threads              thrpt    2  26550.252           ops/ms
RateLimiterBenchmark.tokenBucket_8Threads              thrpt    2  22775.962           ops/ms
RateLimiterBenchmark.factoryCreation_SlidingWindow      avgt    2     90.878           ns/op
RateLimiterBenchmark.factoryCreation_TokenBucket        avgt    2     46.607           ns/op
RateLimiterBenchmark.slidingWindow_16Threads            avgt    2   2121.602           ns/op
RateLimiterBenchmark.slidingWindow_1Thread              avgt    2     49.793           ns/op
RateLimiterBenchmark.slidingWindow_4Threads             avgt    2    601.907           ns/op
RateLimiterBenchmark.slidingWindow_8Threads             avgt    2   1115.335           ns/op
RateLimiterBenchmark.tokenBucket_16Threads              avgt    2   2197.598           ns/op
RateLimiterBenchmark.tokenBucket_1Thread                avgt    2     44.672           ns/op
RateLimiterBenchmark.tokenBucket_4Threads               avgt    2    430.032           ns/op
RateLimiterBenchmark.tokenBucket_8Threads               avgt    2    972.019           ns/op
```
