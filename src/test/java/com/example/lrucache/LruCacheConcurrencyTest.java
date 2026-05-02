package com.example.lrucache;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

final class LruCacheConcurrencyTest {
    @Test
    void concurrentStressNoRaces() throws Exception {
        int threads = 8;
        int opsPerThread = 100000;
        int capacity = 1024;
        LruCache<Integer, Integer> cache = new LruCache<>(capacity);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>(threads);

        for (int i = 0; i < threads; i++) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            futures.add(executor.submit(() -> {
                start.await();
                for (int j = 0; j < opsPerThread; j++) {
                    int key = random.nextInt(capacity * 4);
                    int op = random.nextInt(100);
                    if (op < 50) {
                        cache.put(key, key);
                    } else if (op < 90) {
                        cache.get(key);
                    } else {
                        cache.remove(key);
                    }
                }
                return null;
            }));
        }

        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        assertTrue(cache.size() <= capacity);
    }
}
