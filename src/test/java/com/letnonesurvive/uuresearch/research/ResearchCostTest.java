package com.letnonesurvive.uuresearch.research;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ResearchCostTest {

    @Test
    void usesRegistryCostWhenPresent() {
        assertEquals(3500, ResearchCost.resolveMilliUU(3500, 1));
    }

    @Test
    void fallsBackToDefaultWhenMissingOrInvalid() {
        assertEquals(2000, ResearchCost.resolveMilliUU(null, 2));
        assertEquals(2000, ResearchCost.resolveMilliUU(0, 2));
        assertEquals(2000, ResearchCost.resolveMilliUU(-5, 2));
    }

    @Test
    void totalEuScalesWithCost() {
        assertEquals(10_000, ResearchCost.totalEu(1000, 10_000));
        assertEquals(35_000, ResearchCost.totalEu(3500, 10_000));
    }

    @Test
    void totalEuIsAtLeastOne() {
        assertEquals(1, ResearchCost.totalEu(1, 1));
    }

    @Test
    void totalEuSaturatesInsteadOfOverflowing() {
        assertEquals(Integer.MAX_VALUE, ResearchCost.totalEu(Integer.MAX_VALUE, 1_000_000));
    }

    @Test
    void ticksRoundUpToWholeTicks() {
        assertEquals(313, ResearchCost.ticks(10_000, 32));
        assertEquals(1, ResearchCost.ticks(1, 32));
    }

    @Test
    void formatsUUWithoutTrailingZeros() {
        assertEquals("9", ResearchCost.formatUU(9000));
        assertEquals("1.25", ResearchCost.formatUU(1250));
        assertEquals("0.062", ResearchCost.formatUU(62));
    }

    @Test
    void splitsTicksIntoMinutesAndSeconds() {
        assertArrayEquals(new int[]{2, 21}, ResearchCost.minutesSeconds(2813));
        assertArrayEquals(new int[]{0, 16}, ResearchCost.minutesSeconds(313));
        assertArrayEquals(new int[]{0, 1}, ResearchCost.minutesSeconds(1));
    }
}
