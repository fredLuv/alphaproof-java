package com.example.cache;

import java.util.Optional;

/**
 * Generic bounded key-value cache abstraction.
 */
public interface Cache<K, V> {

    Optional<V> get(K key);

    void put(K key, V value);

    int size();

    int capacity();
}
