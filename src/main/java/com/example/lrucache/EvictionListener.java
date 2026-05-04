package com.example.lrucache;

@FunctionalInterface
public interface EvictionListener<K, V> {
    void onEvict(K key, V value, EvictionReason reason);

    enum EvictionReason {
        CAPACITY,
        EXPIRED,
        MANUAL
    }
}
