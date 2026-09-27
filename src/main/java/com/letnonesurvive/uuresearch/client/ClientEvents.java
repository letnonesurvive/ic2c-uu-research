package com.letnonesurvive.uuresearch.client;

import com.letnonesurvive.uuresearch.UUResearch;
import com.letnonesurvive.uuresearch.UUResearchConfig;
import com.letnonesurvive.uuresearch.machine.ResearchStationBlockEntity;
import com.letnonesurvive.uuresearch.research.ResearchCost;
import com.letnonesurvive.uuresearch.research.ResearchTarget;
import com.letnonesurvive.uuresearch.research.UURecipeIndex;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

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
        Item target = ResearchTarget.of(event.getItemStack().getItem());
        if (!UURecipeIndex.hasUURecipe(player.level.getRecipeManager(), target)) {
            return;
        }
        if (ClientKnowledge.isLearned(ForgeRegistries.ITEMS.getKey(target))) {
            event.getToolTip().add(Component.translatable("tooltip.uuresearch.learned").withStyle(ChatFormatting.GREEN));
        } else if (Screen.hasShiftDown()) {
            int milliUU = UURecipeIndex.milliUUCost(target, UUResearchConfig.DEFAULT_COST_UU.get());
            int ticks = ResearchCost.ticks(ResearchCost.totalEu(milliUU, UUResearchConfig.EU_PER_UU.get()),
                    ResearchStationBlockEntity.ENERGY_PER_TICK);
            int[] time = ResearchCost.minutesSeconds(ticks);
            Component duration = time[0] > 0
                    ? Component.translatable("tooltip.uuresearch.time.minutes", time[0], time[1])
                    : Component.translatable("tooltip.uuresearch.time.seconds", time[1]);
            event.getToolTip().add(Component.translatable("tooltip.uuresearch.researchable",
                    ResearchCost.formatUU(milliUU), duration).withStyle(ChatFormatting.YELLOW));
        }
    }
}
