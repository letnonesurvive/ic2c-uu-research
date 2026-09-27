package com.letnonesurvive.uuresearch;

import com.letnonesurvive.uuresearch.network.ModNetwork;
import com.letnonesurvive.uuresearch.research.ResearchCommand;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(UUResearch.MOD_ID)
public class UUResearch {

    public static final String MOD_ID = "uuresearch";
    public static final Logger LOGGER = LogUtils.getLogger();

    public UUResearch() {
        ModNetwork.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, UUResearchConfig.SPEC);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ResearchCommand.register(event.getDispatcher(), event.getBuildContext());
    }
}
