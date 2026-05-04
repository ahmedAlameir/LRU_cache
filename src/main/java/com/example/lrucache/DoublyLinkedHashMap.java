package com.example.lrucache;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

final class DoublyLinkedHashMap<K, V> {
    private final Map<K, Node<K, V>> index;
    private final Node<K, V> head;
    private final Node<K, V> tail;

    DoublyLinkedHashMap() {
        this.index = new HashMap<>();
        this.head = new Node<>(null, null);
        this.tail = new Node<>(null, null);
        head.next = tail;
        tail.prev = head;
    }

    int size() {
        return index.size();
    }

    boolean containsKey(K key) {
        return index.containsKey(key);
    }

    V get(K key) {
        Node<K, V> node = index.get(key);
        if (node == null) {
            return null;
        }
        return node.value;
    }

    V put(K key, V value) {
        return put(key, value, 0L);
    }

    V put(K key, V value, long expiryNanos) {
        Objects.requireNonNull(key, "key");
        Node<K, V> existing = index.get(key);
        if (existing != null) {
            V oldValue = existing.value;
            existing.value = value;
            existing.expiryNanos = expiryNanos;
            unlink(existing);
            addToFront(existing);
            return oldValue;
        }
        Node<K, V> node = new Node<>(key, value);
        node.expiryNanos = expiryNanos;
        index.put(key, node);
        addToFront(node);
        return null;
    }

    V remove(K key) {
        Node<K, V> node = index.remove(key);
        if (node == null) {
            return null;
        }
        unlink(node);
        return node.value;
    }

    void clear() {
        index.clear();
        head.next = tail;
        tail.prev = head;
    }

    K removeLast() {
        if (tail.prev == head) {
            return null;
        }
        Node<K, V> last = tail.prev;
        unlink(last);
        index.remove(last.key);
        return last.key;
    }

    void moveToFront(K key) {
        Node<K, V> node = index.get(key);
        if (node != null) {
            unlink(node);
            addToFront(node);
        }
    }

    Node<K, V> peekLast() {
        return tail.prev == head ? null : tail.prev;
    }
  
    V peekLastValue() {
        Node<K, V> last = tail.prev == head ? null : tail.prev;
        return last == null ? null : last.value;
    }
    Node<K, V> peekFirst() {
        return head.next == tail ? null : head.next;
    }

    int removeExpired(long nowNanos , BiConsumer<K, V> onEvict) {
        int removed = 0;
        Node<K, V> current = tail.prev;
        while (current != head) {
            Node<K, V> prev = current.prev;
            if (current.expiryNanos > 0L && current.expiryNanos <= nowNanos) {
                index.remove(current.key);
                unlink(current);
                onEvict.accept(current.key, current.value);
                removed++;
            }
            current = prev;
        }
        return removed;
    }

    boolean isExpired(K key, long nowNanos) {
        Node<K, V> node = index.get(key);
        if (node == null)
            return false;
        return node.expiryNanos > 0 && nowNanos > node.expiryNanos;
    }
    private void addToFront(Node<K, V> node) {
        Node<K, V> first = head.next;
        head.next = node;
        node.prev = head;
        node.next = first;
        first.prev = node;
    }

    private void unlink(Node<K, V> node) {
        Node<K, V> prevNode = node.prev;
        Node<K, V> nextNode = node.next;

        prevNode.next = nextNode;
        nextNode.prev = prevNode;

        node.prev = null;
        node.next = null;
    }

    static final class Node<K, V> {
        final K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;
        long expiryNanos; 
        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }
}
