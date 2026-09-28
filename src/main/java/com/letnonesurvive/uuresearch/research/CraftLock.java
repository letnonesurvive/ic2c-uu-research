package com.letnonesurvive.uuresearch.research;

import com.letnonesurvive.uuresearch.UUResearchConfig;
import com.letnonesurvive.uuresearch.client.ClientKnowledge;
import ic2.core.platform.recipes.crafting.RecipeIC2Base;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;

/**
 * Decides whether an IC2 UU-Matter recipe may be crafted. Called from recipe matching, which runs for every
 * recipe on every crafting grid change, so checks go from cheapest to most expensive and stop early.
 */
public final class CraftLock {

    private CraftLock() {
    }

    public static boolean blocks(boolean enabled, boolean hidden, BooleanSupplier learned, BooleanSupplier usesUUMatter) {
        return enabled && hidden && !learned.getAsBoolean() && usesUUMatter.getAsBoolean();
    }

    /** True if the recipe must not match in this level because its UU-Matter recipe is not researched yet. */
    public static boolean shouldBlock(CraftingRecipe recipe, @Nullable Level level) {
        if (level == null || !UUResearchConfig.SPEC.isLoaded()) {
            return false;
        }
        return blocks(UUResearchConfig.REQUIRE_RESEARCH_TO_CRAFT.get(),
                recipe instanceof RecipeIC2Base base && base.isHidden(),
                () -> isLearned(UURecipeIndex.outputId(recipe), level),
                () -> UURecipeIndex.isUURecipe(recipe));
    }

    private static boolean isLearned(ResourceLocation id, Level level) {
        if (level.isClientSide) {
            return ClientKnowledge.isLearned(id);
        }
        MinecraftServer server = level.getServer();
        return server != null && ResearchKnowledge.get(server).isLearned(id);
    }
}
