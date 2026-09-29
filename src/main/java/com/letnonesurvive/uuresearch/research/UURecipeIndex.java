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

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Locates IC2 Classic UU-Matter replication recipes: IC2 crafting recipes made only of UU-Matter, including the
 * ones IC2 shows (iridium ore). Lookups by output are cached per {@link RecipeManager}; {@link #invalidate()} must
 * be called when recipes reload.
 */
public final class UURecipeIndex {

    private static final IdentityCache<RecipeManager, Map<Item, CraftingRecipe>> BY_OUTPUT = new IdentityCache<>();

    private UURecipeIndex() {
    }

    public static boolean isUURecipe(CraftingRecipe recipe) {
        if (!(recipe instanceof RecipeIC2Base)) {
            return false;
        }
        ItemStack uuMatter = new ItemStack(IC2Items.UUMATTER);
        boolean any = false;
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            if (!ingredient.test(uuMatter)) {
                return false;
            }
            any = true;
        }
        return any;
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

    @Nullable
    public static CraftingRecipe recipeFor(RecipeManager manager, Item item) {
        return BY_OUTPUT.get(manager, UURecipeIndex::collectRecipes).get(item);
    }

    public static boolean hasUURecipe(RecipeManager manager, Item item) {
        return recipeFor(manager, item) != null;
    }

    public static void invalidate() {
        BY_OUTPUT.invalidate();
    }

    private static Map<Item, CraftingRecipe> collectRecipes(RecipeManager manager) {
        Map<Item, CraftingRecipe> recipes = new HashMap<>();
        for (CraftingRecipe recipe : all(manager)) {
            recipes.putIfAbsent(recipe.getResultItem().getItem(), recipe);
        }
        return recipes;
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
