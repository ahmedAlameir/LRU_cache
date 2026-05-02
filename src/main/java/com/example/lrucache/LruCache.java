package com.example.lrucache;

import java.util.concurrent.locks.StampedLock;

public final class LruCache<K, V> {
    private final int capacity;
    private final DoublyLinkedHashMap<K, V> map;
    private final StampedLock lock;

    public LruCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.map = new DoublyLinkedHashMap<>();
        this.lock = new StampedLock();
    }

    public V get(K key) {
        long stamp = lock.writeLock();
        try {
            V value = map.get(key);
            if (value != null) {
                map.moveToFront(key);
            }
            return value;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public void put(K key, V value) {
        long stamp = lock.writeLock();
        try {
            if (map.size() >= capacity && !map.containsKey(key)) {
                map.removeLast();
            }
            map.put(key, value);
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
}