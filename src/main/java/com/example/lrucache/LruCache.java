package com.example.lrucache;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.StampedLock;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

public final class LruCache<K, V> implements AutoCloseable, CacheMXBean {
    private static final long DEFAULT_SWEEP_MILLIS = 1000L;
    private final int capacity;
    private final DoublyLinkedHashMap<K, V> map;
    private final StampedLock lock;
    private final ScheduledExecutorService scheduler;
    private final EvictionListener<? super K, ? super V> evictionListener;
    private final LongAdder hits;
    private final LongAdder misses;
    private final LongAdder evictions;
    private final MBeanServer mbeanServer;
    private final ObjectName mbeanName;

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
        this.hits = new LongAdder();
        this.misses = new LongAdder();
        this.evictions = new LongAdder();
        this.mbeanServer = ManagementFactory.getPlatformMBeanServer();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("lru-expirer-", 0).factory());
        this.scheduler.scheduleAtFixedRate(
                this::expireEntriesSafely,
                DEFAULT_SWEEP_MILLIS,
                DEFAULT_SWEEP_MILLIS,
                TimeUnit.MILLISECONDS);
        try {
            this.mbeanName = registerMBean();
        } catch (RuntimeException ex) {
            scheduler.shutdown();
            throw ex;
        }
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
        if (value == null) {
            misses.increment();
        } else {
            hits.increment();
        }
        if (evictedValue != null) {
            evictions.increment();
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
            evictions.increment();
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
            evictions.increment();
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
        int cleared;
        long stamp = lock.writeLock();
        try {
            cleared = map.size();
            map.clear();
        } finally {
            lock.unlockWrite(stamp);
        }
        if (cleared > 0)
            evictions.add(cleared);
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
        try {
            mbeanServer.unregisterMBean(mbeanName);
        } catch (InstanceNotFoundException | MBeanRegistrationException ignored) {
            // Ignore if the bean was already unregistered.
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
            evictions.increment();
            notifyEviction(keys.get(i), values.get(i), EvictionListener.EvictionReason.EXPIRED);
        }
    }

    @Override
    public long getHits() {
        return hits.sum();
    }

    @Override
    public long getMisses() {
        return misses.sum();
    }

    @Override
    public long getEvictions() {
        return evictions.sum();
    }

    @Override
    public int getSize() {
        return size();
    }

    @Override
    public double getHitRatio() {
        long hitCount = hits.sum();
        long missCount = misses.sum();
        long total = hitCount + missCount;
        return total == 0 ? 0.0 : (double) hitCount / total;
    }

    private void notifyEviction(K key, V value, EvictionListener.EvictionReason reason) {
        if (evictionListener == null)
            return;
        Thread.ofVirtual()
                .name("lru-eviction-", 0)
                .start(() -> evictionListener.onEvict(key, value, reason));
    }

    private ObjectName registerMBean() {
        try {
            ObjectName name = new ObjectName("com.example.lrucache:type=LruCache,name="
                    + Integer.toHexString(System.identityHashCode(this)));
            mbeanServer.registerMBean(this, name);
            return name;
        } catch (MalformedObjectNameException | InstanceAlreadyExistsException
                 | MBeanRegistrationException | NotCompliantMBeanException e) {
            throw new IllegalStateException("Failed to register cache MBean", e);
        }
    }
}