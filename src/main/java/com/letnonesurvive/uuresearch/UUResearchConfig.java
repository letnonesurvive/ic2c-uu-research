package com.letnonesurvive.uuresearch;

import net.minecraftforge.common.ForgeConfigSpec;

public final class UUResearchConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue EU_PER_UU;
    public static final ForgeConfigSpec.IntValue DEFAULT_COST_UU;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_RESEARCH_TO_CRAFT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        EU_PER_UU = builder
                .comment("EU needed per 1 UU-Matter of an item's replication cost")
                .defineInRange("euPerUU", 10_000, 1, 1_000_000);
        DEFAULT_COST_UU = builder
                .comment("Replication cost in UU-Matter used when IC2 has no cost entry for the item")
                .defineInRange("defaultCostUU", 1, 1, 1_000);
        REQUIRE_RESEARCH_TO_CRAFT = builder
                .comment("UU-Matter recipes can only be crafted after the item has been researched")
                .define("requireResearchToCraft", true);
        SPEC = builder.build();
    }

    private UUResearchConfig() {
    }
}
