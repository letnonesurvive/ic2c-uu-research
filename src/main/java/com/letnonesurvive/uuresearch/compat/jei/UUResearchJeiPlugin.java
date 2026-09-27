package com.letnonesurvive.uuresearch.compat.jei;

import com.letnonesurvive.uuresearch.UUResearch;
import com.letnonesurvive.uuresearch.client.ClientKnowledge;
import com.letnonesurvive.uuresearch.research.UURecipeIndex;
import ic2.core.IC2;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows researched UU-Matter recipes and hides the rest. Runs deferred so it always applies after
 * IC2's own plugin, which toggles every secret recipe on start and on each config reload.
 */
@JeiPlugin
public class UUResearchJeiPlugin implements IModPlugin {

    private static IJeiRuntime runtime;
    private static boolean listenersRegistered;

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return new ResourceLocation(UUResearch.MOD_ID, "jei");
    }

    @Override
    public void onRuntimeAvailable(@NotNull IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        if (!listenersRegistered) {
            listenersRegistered = true;
            ClientKnowledge.addListener(UUResearchJeiPlugin::scheduleRefresh);
            IC2.CONFIG.addLoadedListener(UUResearchJeiPlugin::scheduleRefresh);
        }
        scheduleRefresh();
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    private static void scheduleRefresh() {
        Minecraft.getInstance().tell(UUResearchJeiPlugin::refresh);
    }

    private static void refresh() {
        IJeiRuntime current = runtime;
        LocalPlayer player = Minecraft.getInstance().player;
        if (current == null || player == null) {
            return;
        }
        List<CraftingRecipe> learned = new ArrayList<>();
        List<CraftingRecipe> unknown = new ArrayList<>();
        for (CraftingRecipe recipe : UURecipeIndex.all(player.connection.getRecipeManager())) {
            (ClientKnowledge.isLearned(UURecipeIndex.outputId(recipe)) ? learned : unknown).add(recipe);
        }
        IRecipeManager manager = current.getRecipeManager();
        manager.hideRecipes(RecipeTypes.CRAFTING, unknown);
        manager.unhideRecipes(RecipeTypes.CRAFTING, learned);
    }
}
