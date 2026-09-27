package com.letnonesurvive.uuresearch.client;

import com.letnonesurvive.uuresearch.UUResearch;
import com.letnonesurvive.uuresearch.research.UURecipeIndex;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
}
