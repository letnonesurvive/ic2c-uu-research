package com.letnonesurvive.uuresearch;

import com.letnonesurvive.uuresearch.network.ModNetwork;
import com.letnonesurvive.uuresearch.research.CraftLockNotifier;
import com.letnonesurvive.uuresearch.research.ResearchKnowledge;
import com.letnonesurvive.uuresearch.research.UURecipeIndex;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = UUResearch.MOD_ID)
public final class ServerEvents {

    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModNetwork.sendTo(player, ResearchKnowledge.get(player.server).learned());
        }
    }

    // Fired after /reload and on player join, i.e. whenever server recipes may have changed
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        UURecipeIndex.invalidate();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            CraftLockNotifier.tick(event.getServer());
        }
    }
}
