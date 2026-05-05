# LRU Cache (Java)

A thread-safe LRU cache with TTL, eviction callbacks, and JMX metrics. Benchmarks are run with JMH.

## Benchmark Results

Commands used:

```
mvn -q -DskipTests package
java -jar target/benchmarks.jar -f 3 -bm thrpt -prof gc -rf json -rff target/jmh-results-f3-gc.json
java -jar target/benchmarks.jar -bm thrpt -bm sample -tu ns -rf json -rff target/jmh-results.json
```

| Scenario | Threads | Throughput (ops/s) | p99 latency (ns/op) | Hit ratio | GC alloc rate (MB/s) | GC alloc (B/op) |
|---|---:|---:|---:|---:|---:|---:|
| Single-thread | 1 | 16.70M | 63.00 | 0.50 | 381.36 | 23.954 |
| Multi-thread | 16 | 90.78M | 248.65 | 0.50 | 2019.77 | 23.954 |
| Multi-thread | 64 | 90.04M | 1140.96 | 0.50 | 1977.68 | 23.955 |

Notes:
- Throughput and GC allocation numbers are from the forks=3 throughput run with `-prof gc`.
- p99 latency values are from the sample-time run (forks=1).
- Hit ratio is derived from hits / (hits + misses) and remains ~0.50 in this workload.
