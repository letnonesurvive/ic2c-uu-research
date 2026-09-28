package com.letnonesurvive.uuresearch.network;

import com.letnonesurvive.uuresearch.UUResearch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashSet;
import java.util.Set;

public final class ModNetwork {

    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(UUResearch.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(KnowledgeSyncPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(KnowledgeSyncPacket::encode)
                .decoder(KnowledgeSyncPacket::decode)
                .consumerMainThread(KnowledgeSyncPacket::handle)
                .add();
        CHANNEL.messageBuilder(RecipeLockedPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RecipeLockedPacket::encode)
                .decoder(RecipeLockedPacket::decode)
                .consumerMainThread(RecipeLockedPacket::handle)
                .add();
    }

    public static void sendTo(ServerPlayer player, Set<ResourceLocation> learned) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new KnowledgeSyncPacket(new HashSet<>(learned)));
    }

    public static void sendToAll(Set<ResourceLocation> learned) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new KnowledgeSyncPacket(new HashSet<>(learned)));
    }

    public static void sendRecipeLocked(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new RecipeLockedPacket());
    }
}
