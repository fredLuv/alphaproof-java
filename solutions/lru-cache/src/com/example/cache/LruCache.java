package com.example.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * O(1) Least-Recently-Used cache backed by a HashMap and an intrusive doubly-linked list.
 */
public final class LruCache<K, V> implements Cache<K, V> {

    private static final class Node<K, V> {
        private final K key;
        private V value;
        private Node<K, V> prev;
        private Node<K, V> next;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private final int capacity;
    private final Map<K, Node<K, V>> index;
    private final Node<K, V> head; // most recently used sentinel
    private final Node<K, V> tail; // least recently used sentinel

    public LruCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be > 0, got: " + capacity);
        }
        this.capacity = capacity;
        this.index = new HashMap<>(capacity * 2);
        this.head = new Node<>(null, null);
        this.tail = new Node<>(null, null);
        this.head.next = this.tail;
        this.tail.prev = this.head;
    }

    @Override
    public Optional<V> get(K key) {
        Objects.requireNonNull(key, "key must not be null");
        Node<K, V> node = index.get(key);
        if (node == null) {
            return Optional.empty();
        }
        moveToFront(node);
        return Optional.of(node.value);
    }

    @Override
    public void put(K key, V value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        Node<K, V> existing = index.get(key);
        if (existing != null) {
            existing.value = value;
            moveToFront(existing);
            return;
        }
        if (index.size() >= capacity) {
            Node<K, V> lru = tail.prev;
            if (lru != head) {
                unlink(lru);
                index.remove(lru.key);
            }
        }
        Node<K, V> inserted = new Node<>(key, value);
        insertFront(inserted);
        index.put(key, inserted);
    }

    @Override
    public int size() {
        return index.size();
    }

    @Override
    public int capacity() {
        return capacity;
    }

    private void moveToFront(Node<K, V> node) {
        unlink(node);
        insertFront(node);
    }

    private void insertFront(Node<K, V> node) {
        Node<K, V> first = head.next;
        node.prev = head;
        node.next = first;
        head.next = node;
        first.prev = node;
    }

    private void unlink(Node<K, V> node) {
        Node<K, V> p = node.prev;
        Node<K, V> n = node.next;
        p.next = n;
        n.prev = p;
        node.prev = null;
        node.next = null;
    }
}
