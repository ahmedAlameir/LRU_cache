package com.example.lrucache;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;
import java.util.function.BiConsumer;

public final class LruCache<K, V> implements AutoCloseable {
    private static final long DEFAULT_SWEEP_MILLIS = 1000L;
    private final int capacity;
    private final DoublyLinkedHashMap<K, V> map;
    private final StampedLock lock;
    private final ScheduledExecutorService scheduler;
    private final EvictionListener<? super K, ? super V> evictionListener;

    public LruCache(int capacity) {
        this(capacity, null);
    }

    public LruCache(int capacity, EvictionListener<? super K, ? super V> evictionListener) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.map = new DoublyLinkedHashMap<>();
        this.lock = new StampedLock();
        this.evictionListener = evictionListener;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("lru-expirer-", 0).factory());
        this.scheduler.scheduleAtFixedRate(
                this::expireEntriesSafely,
                DEFAULT_SWEEP_MILLIS,
                DEFAULT_SWEEP_MILLIS,
                TimeUnit.MILLISECONDS);
    }

    public V get(K key) {
        V value;
        V evictedValue = null;
        long stamp = lock.writeLock();
        try {
            if (map.isExpired(key, System.nanoTime())) {
                evictedValue = map.remove(key);
                value = null;
            } else {
                value = map.get(key);
                if (value != null) {
                    map.moveToFront(key);
                }
            }
        } finally {
            lock.unlockWrite(stamp);
        }
        if (evictedValue != null) {
            notifyEviction(key, evictedValue, EvictionListener.EvictionReason.EXPIRED);
        }
        return value;
    }

    public void put(K key, V value) {
        putWithExpiry(key, value, 0L);
    }

    public void put(K key, V value, long ttl, TimeUnit unit) {
        if (ttl < 0) throw new IllegalArgumentException("ttl must be non-negative");
        long expiryNanos = 0L;
        if (ttl > 0L) {
            Objects.requireNonNull(unit, "unit");
            long ttlNanos = unit.toNanos(ttl);
            if (ttlNanos > 0L) {
                long now = System.nanoTime();
                try {
                    expiryNanos = Math.addExact(now, ttlNanos);
                } catch (ArithmeticException ex) {
                    expiryNanos = Long.MAX_VALUE;
                }
            }
        }
        putWithExpiry(key, value, expiryNanos);
    }

    private void putWithExpiry(K key, V value, long expiryNanos) {
        long stamp = lock.writeLock();
        V evictedValue = null;
        K evictedKey = null;

        try {
            if (map.size() >= capacity && !map.containsKey(key)) {
               evictedValue = map.peekLastValue();
                evictedKey = map.removeLast();
            }
            map.put(key, value, expiryNanos);
        } finally {
            lock.unlockWrite(stamp);
        }
        if (evictedValue != null) {
            notifyEviction(evictedKey, evictedValue, EvictionListener.EvictionReason.CAPACITY);
        }
    }

    public V remove(K key) {
        V value;
        long stamp = lock.writeLock();
        try {
            value = map.remove(key);
        } finally {
            lock.unlockWrite(stamp);
        }
        if (value != null) {
            notifyEviction(key, value, EvictionListener.EvictionReason.MANUAL);
        }
        return value;
    }

    public int size() {
        long stamp = lock.tryOptimisticRead();
        int s = map.size();
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                s = map.size();
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return s;
    }

    public void clear() {
        long stamp = lock.writeLock();
        try {
            map.clear();
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public void close() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void expireEntriesSafely() {
        try {
            expireEntries();
        } catch (RuntimeException ignored) {
            // Avoid terminating the scheduler on unexpected errors.
        }
    }

    private void expireEntries() {
        var keys = new ArrayList<K>();
        var values = new ArrayList<V>();
        long stamp = lock.tryWriteLock();
        if (stamp == 0L)
            return;
        try {
            map.removeExpired(System.nanoTime(), (k, v) -> {
                keys.add(k);
                values.add(v);
            });
        } finally {
            lock.unlockWrite(stamp);
        }
        for (int i = 0; i < keys.size(); i++) {
            notifyEviction(keys.get(i), values.get(i), EvictionListener.EvictionReason.EXPIRED);
        }
    }

    private void notifyEviction(K key, V value, EvictionListener.EvictionReason reason) {
        if (evictionListener == null)
            return;
        Thread.ofVirtual()
                .name("lru-eviction-", 0)
                .start(() -> evictionListener.onEvict(key, value, reason));
    }
}