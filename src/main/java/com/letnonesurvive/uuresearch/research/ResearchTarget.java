package com.letnonesurvive.uuresearch.research;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/**
 * Maps a sample to the item whose UU recipes it researches. A fluid bucket stands in for its fluid block
 * (IC2 registers items for the water and lava blocks, obtainable in survival only via their UU recipe).
 */
public final class ResearchTarget {

    private ResearchTarget() {
    }

    public static Item of(Item sample) {
        if (sample instanceof BucketItem bucket && bucket.getFluid() != Fluids.EMPTY) {
            Item fluidBlockItem = bucket.getFluid().defaultFluidState().createLegacyBlock().getBlock().asItem();
            if (fluidBlockItem != Items.AIR) {
                return fluidBlockItem;
            }
        }
        return sample;
    }
}
