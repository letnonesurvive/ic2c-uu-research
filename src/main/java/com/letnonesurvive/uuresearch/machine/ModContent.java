package com.letnonesurvive.uuresearch.machine;

import com.letnonesurvive.uuresearch.UUResearch;
import ic2.core.block.base.IC2TileType;
import ic2.core.block.base.drops.IBlockDropProvider;
import ic2.core.block.machines.BaseMachineBlock;
import ic2.core.platform.rendering.features.ITextureProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid = UUResearch.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModContent {

    public static final ResourceLocation RESEARCH_STATION_ID = new ResourceLocation(UUResearch.MOD_ID, "research_station");

    public static IC2TileType<ResearchStationBlockEntity> RESEARCH_STATION_TYPE;
    public static BaseMachineBlock RESEARCH_STATION;

    private ModContent() {
    }

    // The block entity type is created before the block because BaseMachineBlock needs it,
    // while Forge registers blocks before block entity types.
    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.BLOCKS, helper -> {
            RESEARCH_STATION_TYPE = new IC2TileType<>(ResearchStationBlockEntity::new);
            RESEARCH_STATION = new BaseMachineBlock(RESEARCH_STATION_ID.toString(), IBlockDropProvider.SELF_OR_ADV_MACHINE,
                    ITextureProvider.toggle(UUResearch.MOD_ID, "machine/research_station"), RESEARCH_STATION_TYPE);
            helper.register(RESEARCH_STATION_ID, RESEARCH_STATION);
        });
        event.register(ForgeRegistries.Keys.ITEMS,
                helper -> helper.register(RESEARCH_STATION_ID, RESEARCH_STATION.createItem()));
        event.register(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES,
                helper -> helper.register(RESEARCH_STATION_ID, RESEARCH_STATION_TYPE));
    }
}
