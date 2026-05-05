package com.example.lrucache;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
public class LruCacheBenchmark {
    private static final int OPS = 1_000_000;

    @State(Scope.Thread)
    public static class BenchmarkState {
        private static final int KEY_SPACE = 1 << 16;
        private LruCache<Integer, Integer> cache;
        private int rnd;

        @Setup(Level.Trial)
        public void setUp() {
            cache = new LruCache<>(KEY_SPACE);
            for (int i = 0; i < KEY_SPACE / 2; i++) {
                cache.put(i, i);
            }
            rnd = 0x1234abcd;
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            cache.close();
        }

        int nextKey() {
            rnd = rnd * 1664525 + 1013904223;
            return rnd & (KEY_SPACE - 1);
        }
    }

    @AuxCounters(AuxCounters.Type.EVENTS)
    @State(Scope.Thread)
    public static class Counters {
        public long hits;
        public long misses;
        public double hitRatio;

        @Setup(Level.Iteration)
        public void reset() {
            hits = 0;
            misses = 0;
            hitRatio = 0.0;
        }
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void singleThreadGetPut(BenchmarkState state, Counters counters) {
        runOps(state, counters);
    }

    @Benchmark
    @Threads(16)
    @OperationsPerInvocation(OPS)
    public void sixteenThreadGetPut(BenchmarkState state, Counters counters) {
        runOps(state, counters);
    }

    @Benchmark
    @Threads(64)
    @OperationsPerInvocation(OPS)
    public void sixtyFourThreadGetPut(BenchmarkState state, Counters counters) {
        runOps(state, counters);
    }

    private void runOps(BenchmarkState state, Counters counters) {
        for (int i = 0; i < OPS; i++) {
            int key = state.nextKey();
            if ((i & 1) == 0) {
                state.cache.put(key, key);
            } else {
                Integer value = state.cache.get(key);
                if (value == null) {
                    counters.misses++;
                } else {
                    counters.hits++;
                }
            }
        }
        long total = counters.hits + counters.misses;
        counters.hitRatio = total == 0 ? 0.0 : (double) counters.hits / total;
    }
}
