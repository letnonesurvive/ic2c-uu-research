package com.letnonesurvive.uuresearch.research;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Thread-safe memo keyed by object identity. Callers must {@link #invalidate()} when the
 * underlying data changes, which also releases the stale keys.
 */
public final class IdentityCache<K, V> {

    private final Map<K, V> values = new IdentityHashMap<>();

    public synchronized V get(K key, Function<K, V> compute) {
        return values.computeIfAbsent(key, compute);
    }

    public synchronized void invalidate() {
        values.clear();
    }
}
