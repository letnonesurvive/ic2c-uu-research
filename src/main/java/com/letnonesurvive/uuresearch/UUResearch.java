package com.letnonesurvive.uuresearch;

import com.letnonesurvive.uuresearch.network.ModNetwork;
import com.letnonesurvive.uuresearch.research.CraftingGrids;
import com.letnonesurvive.uuresearch.research.ResearchCommand;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

@Mod(UUResearch.MOD_ID)
public class UUResearch {

    public static final String MOD_ID = "uuresearch";
    public static final Logger LOGGER = LogUtils.getLogger();

    public UUResearch() {
        ModNetwork.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, UUResearchConfig.SPEC);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigReloaded);
    }

    // Toggling requireResearchToCraft must update results already sitting in crafting grids
    private void onConfigReloaded(ModConfigEvent.Reloading event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (event.getConfig().getSpec() == UUResearchConfig.SPEC && server != null) {
            server.execute(() -> CraftingGrids.refresh(server));
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ResearchCommand.register(event.getDispatcher(), event.getBuildContext());
    }
}
