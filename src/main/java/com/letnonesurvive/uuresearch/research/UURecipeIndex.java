package com.letnonesurvive.uuresearch.research;

import ic2.api.recipes.registries.IUUMatterRegistry;
import ic2.core.IC2;
import ic2.core.platform.recipes.crafting.RecipeIC2Base;
import ic2.core.platform.registries.IC2Items;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Locates IC2 Classic UU-Matter replication recipes: hidden IC2 crafting recipes that use UU-Matter.
 * Output lookups are cached per {@link RecipeManager}; {@link #invalidate()} must be called when recipes reload.
 */
public final class UURecipeIndex {

    private static final IdentityCache<RecipeManager, Set<Item>> OUTPUTS = new IdentityCache<>();

    private UURecipeIndex() {
    }

    public static boolean isUURecipe(CraftingRecipe recipe) {
        if (!(recipe instanceof RecipeIC2Base base) || !base.isHidden()) {
            return false;
        }
        ItemStack uuMatter = new ItemStack(IC2Items.UUMATTER);
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (!ingredient.isEmpty() && ingredient.test(uuMatter)) {
                return true;
            }
        }
        return false;
    }

    public static List<CraftingRecipe> all(RecipeManager manager) {
        return manager.getAllRecipesFor(RecipeType.CRAFTING).stream()
                .filter(UURecipeIndex::isUURecipe)
                .toList();
    }

    public static ResourceLocation outputId(CraftingRecipe recipe) {
        return ForgeRegistries.ITEMS.getKey(recipe.getResultItem().getItem());
    }

    public static Set<ResourceLocation> outputIds(RecipeManager manager) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (CraftingRecipe recipe : all(manager)) {
            ids.add(outputId(recipe));
        }
        return ids;
    }

    public static boolean hasUURecipe(RecipeManager manager, Item item) {
        return OUTPUTS.get(manager, UURecipeIndex::collectOutputs).contains(item);
    }

    public static void invalidate() {
        OUTPUTS.invalidate();
    }

    private static Set<Item> collectOutputs(RecipeManager manager) {
        Set<Item> items = new HashSet<>();
        for (CraftingRecipe recipe : all(manager)) {
            items.add(recipe.getResultItem().getItem());
        }
        return items;
    }

    /** Cheapest registered UU cost of the item in milli-UU, from IC2's registry of the calling thread's side. */
    public static int milliUUCost(Item item, int defaultCostUU) {
        Integer cheapest = null;
        for (IUUMatterRegistry.UUMatterEntry entry : IC2.RECIPES.get().UU.getEntries()) {
            if (entry.getStack().getItem() == item) {
                cheapest = cheapest == null ? entry.getUUNeeded() : Math.min(cheapest, entry.getUUNeeded());
            }
        }
        return ResearchCost.resolveMilliUU(cheapest, defaultCostUU);
    }
}
