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
| tokenBucket_1Thread | 1 | 22050.214 | NaN | ops/ms |
| tokenBucket_4Threads | 4 | 26550.252 | NaN | ops/ms |
| tokenBucket_8Threads | 8 | 22775.962 | NaN | ops/ms |
| tokenBucket_16Threads | 16 | 8847.620 | NaN | ops/ms |

## Token Bucket — Average Time (ns/op)

| Benchmark | Threads | Score | Error | Units |
|---|---|---|---|---|
| tokenBucket_1Thread | 1 | 44.672 | NaN | ns/op |
| tokenBucket_4Threads | 4 | 430.032 | NaN | ns/op |
| tokenBucket_8Threads | 8 | 972.019 | NaN | ns/op |
| tokenBucket_16Threads | 16 | 2197.598 | NaN | ns/op |

---

## Sliding Window — Throughput (ops/ms)

| Benchmark | Threads | Score | Error | Units |
|---|---|---|---|---|
| slidingWindow_1Thread | 1 | 18592.128 | NaN | ops/ms |
| slidingWindow_4Threads | 4 | 17000.687 | NaN | ops/ms |
| slidingWindow_8Threads | 8 | 14843.053 | NaN | ops/ms |
| slidingWindow_16Threads | 16 | 16167.424 | NaN | ops/ms |

## Sliding Window — Average Time (ns/op)

| Benchmark | Threads | Score | Error | Units |
|---|---|---|---|---|
| slidingWindow_1Thread | 1 | 49.793 | NaN | ns/op |
| slidingWindow_4Threads | 4 | 601.907 | NaN | ns/op |
| slidingWindow_8Threads | 8 | 1115.335 | NaN | ns/op |
| slidingWindow_16Threads | 16 | 2121.602 | NaN | ns/op |

---

## Factory Creation Overhead (ns/op)

| Benchmark | Score | Error | Units |
|---|---|---|---|
| factoryCreation_TokenBucket | 46.607 | NaN | ns/op |
| factoryCreation_SlidingWindow | 90.878 | NaN | ns/op |

---

## Raw JMH Output

```
# Paste full JMH console output here after running benchmarks
```
