package com.letnonesurvive.uuresearch.network;

import com.letnonesurvive.uuresearch.client.LockedMessage;
import net.minecraft.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: the crafting grid shows an unresearched UU recipe. Drawn over the open screen by the client,
 * since vanilla action bar messages are hidden behind container screens.
 */
public record RecipeLockedPacket() {

    public void encode(FriendlyByteBuf buf) {
    }

    public static RecipeLockedPacket decode(FriendlyByteBuf buf) {
        return new RecipeLockedPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LockedMessage.show(Util.getMillis()));
    }
}
