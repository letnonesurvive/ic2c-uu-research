package com.letnonesurvive.uuresearch.network;

import com.letnonesurvive.uuresearch.client.ClientKnowledge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Server to client: the complete set of researched item ids.
 */
public record KnowledgeSyncPacket(Set<ResourceLocation> learned) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeCollection(learned, FriendlyByteBuf::writeResourceLocation);
    }

    public static KnowledgeSyncPacket decode(FriendlyByteBuf buf) {
        return new KnowledgeSyncPacket(buf.readCollection(HashSet::new, FriendlyByteBuf::readResourceLocation));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientKnowledge.set(learned));
    }
}
