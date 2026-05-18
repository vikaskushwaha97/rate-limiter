# Benchmark Results

> Run after `mvn package -DskipTests` using:
> ```bash
> java -jar target/rate-limiter-1.0.0-benchmarks.jar -wi 3 -i 5 -f 1 -rf json -rff BENCHMARK_RESULTS.json
> ```
> Then paste your JMH output below and update the resume bullets in README.md.

## Environment

| Property | Value |
|---|---|
| JVM | *(fill in: e.g. OpenJDK 11.0.22)* |
| CPU | *(fill in: e.g. Intel Core i5-1135G7 @ 2.40GHz)* |
| OS | *(fill in: e.g. Windows 11)* |
| JMH version | 1.37 |
| Warmup | 3 iterations × 1s |
| Measurement | 5 iterations × 1s |
| Forks | 1 |

---

## Token Bucket — Throughput (ops/ms)

| Benchmark | Threads | Score | Error | Units |
|---|---|---|---|---|
| tokenBucket_1Thread | 1 | *(TBD)* | *(TBD)* | ops/ms |
| tokenBucket_4Threads | 4 | *(TBD)* | *(TBD)* | ops/ms |
| tokenBucket_8Threads | 8 | *(TBD)* | *(TBD)* | ops/ms |
| tokenBucket_16Threads | 16 | *(TBD)* | *(TBD)* | ops/ms |

## Token Bucket — Average Time (ns/op)

| Benchmark | Threads | Score | Error | Units |
|---|---|---|---|---|
| tokenBucket_1Thread | 1 | *(TBD)* | *(TBD)* | ns/op |
| tokenBucket_4Threads | 4 | *(TBD)* | *(TBD)* | ns/op |
| tokenBucket_8Threads | 8 | *(TBD)* | *(TBD)* | ns/op |
| tokenBucket_16Threads | 16 | *(TBD)* | *(TBD)* | ns/op |

---

## Sliding Window — Throughput (ops/ms)

| Benchmark | Threads | Score | Error | Units |
|---|---|---|---|---|
| slidingWindow_1Thread | 1 | *(TBD)* | *(TBD)* | ops/ms |
| slidingWindow_4Threads | 4 | *(TBD)* | *(TBD)* | ops/ms |
| slidingWindow_8Threads | 8 | *(TBD)* | *(TBD)* | ops/ms |
| slidingWindow_16Threads | 16 | *(TBD)* | *(TBD)* | ops/ms |

## Sliding Window — Average Time (ns/op)

| Benchmark | Threads | Score | Error | Units |
|---|---|---|---|---|
| slidingWindow_1Thread | 1 | *(TBD)* | *(TBD)* | ns/op |
| slidingWindow_4Threads | 4 | *(TBD)* | *(TBD)* | ns/op |
| slidingWindow_8Threads | 8 | *(TBD)* | *(TBD)* | ns/op |
| slidingWindow_16Threads | 16 | *(TBD)* | *(TBD)* | ns/op |

---

## Factory Creation Overhead (ns/op)

| Benchmark | Score | Error | Units |
|---|---|---|---|
| factoryCreation_TokenBucket | *(TBD)* | *(TBD)* | ns/op |
| factoryCreation_SlidingWindow | *(TBD)* | *(TBD)* | ns/op |

---

## Raw JMH Output

```
# Paste full JMH console output here after running benchmarks
```
