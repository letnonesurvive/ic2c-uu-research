package com.letnonesurvive.uuresearch.research;

import org.junit.jupiter.api.Test;

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
}
