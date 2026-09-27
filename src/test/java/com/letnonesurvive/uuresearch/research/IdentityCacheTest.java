package com.letnonesurvive.uuresearch.research;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentityCacheTest {

    @Test
    void computesOncePerKey() {
        IdentityCache<Object, String> cache = new IdentityCache<>();
        AtomicInteger calls = new AtomicInteger();
        Object key = new Object();
        cache.get(key, k -> "v" + calls.incrementAndGet());
        assertEquals("v1", cache.get(key, k -> "v" + calls.incrementAndGet()));
        assertEquals(1, calls.get());
    }

    @Test
    void distinguishesKeysByIdentity() {
        IdentityCache<String, Integer> cache = new IdentityCache<>();
        AtomicInteger calls = new AtomicInteger();
        String a = new String("same");
        String b = new String("same");
        cache.get(a, k -> calls.incrementAndGet());
        cache.get(b, k -> calls.incrementAndGet());
        assertEquals(2, calls.get());
    }

    @Test
    void recomputesAfterInvalidate() {
        IdentityCache<Object, Integer> cache = new IdentityCache<>();
        AtomicInteger calls = new AtomicInteger();
        Object key = new Object();
        cache.get(key, k -> calls.incrementAndGet());
        cache.invalidate();
        assertEquals(2, cache.get(key, k -> calls.incrementAndGet()));
    }
}
