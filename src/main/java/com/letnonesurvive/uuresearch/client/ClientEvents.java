package com.letnonesurvive.uuresearch.client;

import com.letnonesurvive.uuresearch.UUResearch;
import com.letnonesurvive.uuresearch.UUResearchConfig;
import com.letnonesurvive.uuresearch.machine.ResearchStationBlockEntity;
import com.letnonesurvive.uuresearch.research.ResearchCost;
import com.letnonesurvive.uuresearch.research.UURecipeIndex;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

@Mod.EventBusSubscriber(modid = UUResearch.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {
    }

    // Prevents knowledge of one world from leaking into the next one in the same session
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientKnowledge.clear();
    }

    // The client reuses its RecipeManager instance, so identity-keyed caches must be dropped explicitly
    @SubscribeEvent
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        UURecipeIndex.invalidate();
    }

    // "Researched" is always shown; cost and time only with Shift, so tooltips don't spoil what is replicable
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        Player player = event.getEntity();
        if (player == null) {
            return;
        }
        Item target = event.getItemStack().getItem();
        if (!UURecipeIndex.hasUURecipe(player.level.getRecipeManager(), target)) {
            return;
        }
        if (ClientKnowledge.isLearned(ForgeRegistries.ITEMS.getKey(target))) {
            event.getToolTip().add(Component.translatable("tooltip.uuresearch.learned").withStyle(ChatFormatting.GREEN));
        } else if (Screen.hasShiftDown()) {
            // Same numbers as the machine: UU for one craft of the recipe, processed unit by unit at the base rate
            CraftingRecipe recipe = UURecipeIndex.recipeFor(player.level.getRecipeManager(), target);
            int milliUU = UURecipeIndex.milliUUCost(target, UUResearchConfig.DEFAULT_COST_UU.get());
            int need = ResearchCost.uuPerCraft(milliUU, recipe.getResultItem().getCount());
            int perUnit = ResearchCost.euPerUnit(ResearchCost.totalEu(milliUU, UUResearchConfig.EU_PER_UU.get()), need);
            int ticks = need * ResearchCost.ticks(perUnit, ResearchStationBlockEntity.ENERGY_PER_TICK);
            int[] time = ResearchCost.minutesSeconds(ticks);
            Component duration = time[0] > 0
                    ? Component.translatable("tooltip.uuresearch.time.minutes", time[0], time[1])
                    : Component.translatable("tooltip.uuresearch.time.seconds", time[1]);
            event.getToolTip().add(Component.translatable("tooltip.uuresearch.researchable",
                    need, duration).withStyle(ChatFormatting.YELLOW));
        }
    }

    // Drawn last and lifted above items and tooltips so it reads in front of the crafting screen
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        int alpha = (int) (LockedMessage.alpha(LockedMessage.elapsed(Util.getMillis())) * 255);
        if (alpha < 8) {
            // Font renders colours with a near-zero alpha as fully opaque
            return;
        }
        // Vanilla keeps the recipe book and the menu centred together, so the whole UI spans the menu's right edge
        // mirrored around the screen centre; centre and wrap the text on that span.
        int right = screen.getGuiLeft() + screen.getXSize();
        int left = Math.max(0, Math.min(screen.getGuiLeft(), screen.width - right));
        int wrapWidth = Math.max(100, Math.min(right - left, screen.width - 24));
        Font font = Minecraft.getInstance().font;
        List<FormattedCharSequence> lines = font.split(Component.translatable("message.uuresearch.recipe_locked"), wrapWidth);
        int width = 0;
        for (FormattedCharSequence line : lines) {
            width = Math.max(width, font.width(line));
        }
        int height = lines.size() * (font.lineHeight + 1);
        int centre = (left + right) / 2;
        int y = screen.getGuiTop() + screen.getYSize() + 6;
        if (y + height + 3 > screen.height) {
            y = screen.getGuiTop() - height - 6;
        }

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0.0D, 0.0D, 500.0D);
        RenderSystem.enableBlend();
        GuiComponent.fill(pose, centre - width / 2 - 4, y - 3, centre + width / 2 + 4, y + height + 2, (alpha * 3 / 4) << 24);
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            font.drawShadow(pose, line, centre - font.width(line) / 2, y + i * (font.lineHeight + 1), (alpha << 24) | 0xFFFF55);
        }
        RenderSystem.disableBlend();
        pose.popPose();
    }
}
