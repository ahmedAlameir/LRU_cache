package com.example.lrucache;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;

public final class LruCache<K, V> implements AutoCloseable {
    private static final long DEFAULT_SWEEP_MILLIS = 1000L;
    private final int capacity;
    private final DoublyLinkedHashMap<K, V> map;
    private final StampedLock lock;
    private final ScheduledExecutorService scheduler;

    public LruCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.map = new DoublyLinkedHashMap<>();
        this.lock = new StampedLock();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("lru-expirer-", 0).factory());
        this.scheduler.scheduleAtFixedRate(
                this::expireEntriesSafely,
                DEFAULT_SWEEP_MILLIS,
                DEFAULT_SWEEP_MILLIS,
                TimeUnit.MILLISECONDS);
    }

    public V get(K key) {
        long stamp = lock.writeLock();
        try {
            if (map.isExpired(key, System.nanoTime())) {
                map.remove(key);
                return null;
            }
            V value = map.get(key);
            if (value != null)
                map.moveToFront(key);
            return value;
        } finally {
            lock.unlockWrite(stamp);
        }
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
        try {
            if (map.size() >= capacity && !map.containsKey(key)) {
                map.removeLast();
            }
            map.put(key, value, expiryNanos);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public V remove(K key) {
        long stamp = lock.writeLock();
        try {
            return map.remove(key);
        } finally {
            lock.unlockWrite(stamp);
        }
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
        // Try to acquire the write lock without blocking to avoid contention with active operations.
        long stamp = lock.tryWriteLock();
        if (stamp == 0L) {
            return;
        }
        try {
            map.removeExpired(System.nanoTime());
        } finally {
            lock.unlockWrite(stamp);
        }
    }
}