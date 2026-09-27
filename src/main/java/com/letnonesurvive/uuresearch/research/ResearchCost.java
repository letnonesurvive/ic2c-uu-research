package com.letnonesurvive.uuresearch.research;

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
}
