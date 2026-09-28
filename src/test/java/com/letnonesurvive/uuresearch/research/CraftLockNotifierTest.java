package com.letnonesurvive.uuresearch.research;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftLockNotifierTest {

    @Test
    void newPatternStartsTheWait() {
        assertEquals(100, CraftLockNotifier.waitStart(null, false, 100));
        assertEquals(100, CraftLockNotifier.waitStart(50L, false, 100));
    }

    @Test
    void samePatternKeepsWaiting() {
        assertEquals(50, CraftLockNotifier.waitStart(50L, true, 100));
    }

    @Test
    void notDueBeforeDelay() {
        assertFalse(CraftLockNotifier.isDue(139, 100));
    }

    @Test
    void dueAfterDelay() {
        assertTrue(CraftLockNotifier.isDue(140, 100));
    }
}
