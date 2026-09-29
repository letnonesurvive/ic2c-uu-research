package com.letnonesurvive.uuresearch.research;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class CraftLockTest {

    @Test
    void blocksUnlearnedHiddenUURecipe() {
        assertTrue(CraftLock.blocks(true, true, () -> false, () -> true));
    }

    @Test
    void allowsWhenDisabled() {
        assertFalse(CraftLock.blocks(false, true, () -> false, () -> true));
    }

    @Test
    void allowsNonIC2Recipes() {
        assertFalse(CraftLock.blocks(true, false, () -> false, () -> true));
    }

    @Test
    void allowsLearnedRecipes() {
        assertFalse(CraftLock.blocks(true, true, () -> true, () -> true));
    }

    @Test
    void allowsHiddenRecipesWithoutUUMatter() {
        assertFalse(CraftLock.blocks(true, true, () -> false, () -> false));
    }

    @Test
    void stopsAtFirstFalseCheck() {
        AtomicInteger calls = new AtomicInteger();
        BooleanSupplier counted = () -> {
            calls.incrementAndGet();
            return true;
        };
        CraftLock.blocks(false, true, counted, counted);
        CraftLock.blocks(true, false, counted, counted);
        assertEquals(0, calls.get());
        CraftLock.blocks(true, true, counted, counted);
        assertEquals(1, calls.get(), "learned=true must skip the ingredient scan");
    }
}
