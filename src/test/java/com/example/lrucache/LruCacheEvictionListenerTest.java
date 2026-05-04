package com.example.lrucache;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

final class LruCacheEvictionListenerTest {
    private record EvictionEvent(String key, Integer value, EvictionListener.EvictionReason reason) {
    }

    @Test
    void listenerCalledOnCapacityEviction() throws Exception {
        BlockingQueue<EvictionEvent> events = new LinkedBlockingQueue<>();
        try (LruCache<String, Integer> cache = new LruCache<>(1, (k, v, r) ->
                events.add(new EvictionEvent(k, v, r)))) {
            cache.put("a", 1);
            cache.put("b", 2);

            EvictionEvent event = events.poll(1, TimeUnit.SECONDS);
            assertNotNull(event);
            assertEquals("a", event.key());
            assertEquals(1, event.value());
            assertEquals(EvictionListener.EvictionReason.CAPACITY, event.reason());
        }
    }

    @Test
    void listenerCalledOnTtlExpiry() throws Exception {
        BlockingQueue<EvictionEvent> events = new LinkedBlockingQueue<>();
        try (LruCache<String, Integer> cache = new LruCache<>(4, (k, v, r) ->
                events.add(new EvictionEvent(k, v, r)))) {
            cache.put("k", 7, 100, TimeUnit.MILLISECONDS);
            Thread.sleep(200L);
            cache.get("k");

            EvictionEvent event = events.poll(1, TimeUnit.SECONDS);
            assertNotNull(event);
            assertEquals("k", event.key());
            assertEquals(7, event.value());
            assertEquals(EvictionListener.EvictionReason.EXPIRED, event.reason());
        }
    }

    @Test
    void listenerCalledOnExplicitRemove() throws Exception {
        BlockingQueue<EvictionEvent> events = new LinkedBlockingQueue<>();
        try (LruCache<String, Integer> cache = new LruCache<>(4, (k, v, r) ->
                events.add(new EvictionEvent(k, v, r)))) {
            cache.put("x", 9);
            cache.remove("x");

            EvictionEvent event = events.poll(1, TimeUnit.SECONDS);
            assertNotNull(event);
            assertEquals("x", event.key());
            assertEquals(9, event.value());
            assertEquals(EvictionListener.EvictionReason.MANUAL, event.reason());
        }
    }
}
