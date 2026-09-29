package com.letnonesurvive.uuresearch.research;

import java.math.BigDecimal;

/**
 * Energy cost math for researching an item. IC2 expresses UU cost in milli-UU (1000 = one UU-Matter).
 */
public final class ResearchCost {

    public static final int MILLI_UU_PER_UU = 1000;

    private ResearchCost() {
    }

    /** Returns the registry cost if it is positive, otherwise the configured default. */
    public static int resolveMilliUU(Integer registryMilliUU, int defaultCostUU) {
        if (registryMilliUU != null && registryMilliUU > 0) {
            return registryMilliUU;
        }
        return defaultCostUU * MILLI_UU_PER_UU;
    }

    /** Total EU to research an item; at least 1, saturated at {@link Integer#MAX_VALUE}. */
    public static int totalEu(int milliUU, int euPerUU) {
        long eu = (long) milliUU * euPerUU / MILLI_UU_PER_UU;
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, eu));
    }

    /** UU-Matter one craft of the recipe consumes; IC2 stores the per-item cost rounded down. */
    public static int uuPerCraft(int milliUU, int resultCount) {
        return Math.max(1, (int) Math.round((double) milliUU * resultCount / MILLI_UU_PER_UU));
    }

    /** EU to process one UU-Matter unit: an equal share of {@code totalEu}, rounded up, at least 1. */
    public static int euPerUnit(int totalEu, int units) {
        return Math.max(1, (int) (((long) totalEu + units - 1) / Math.max(1, units)));
    }

    /** Ticks to spend {@code totalEu} at {@code euPerTick}, rounded up. */
    public static int ticks(int totalEu, int euPerTick) {
        return (totalEu + euPerTick - 1) / euPerTick;
    }

    /** Milli-UU as a UU amount without trailing zeros, e.g. 1250 -> "1.25". */
    public static String formatUU(int milliUU) {
        return BigDecimal.valueOf(milliUU, 3).stripTrailingZeros().toPlainString();
    }

    /** Whole minutes and seconds (rounded up) of a tick count. */
    public static int[] minutesSeconds(int ticks) {
        int seconds = (ticks + 19) / 20;
        return new int[]{seconds / 60, seconds % 60};
    }
}
