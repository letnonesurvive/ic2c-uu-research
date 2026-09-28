package com.letnonesurvive.uuresearch.research;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;

/**
 * Recomputes the crafting results of online players after the craft lock changed. Otherwise a result computed
 * before the change stays in the slot: a newly researched recipe would not show up until the grid is touched,
 * and taking a result whose recipe became blocked makes vanilla hand the ingredients back (duplication).
 */
public final class CraftingGrids {

    private CraftingGrids() {
    }

    // Vanilla crafting menus ignore the container argument of slotsChanged and recompute their own grid
    public static void refresh(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.inventoryMenu.slotsChanged(player.getInventory());
            if (player.containerMenu instanceof CraftingMenu menu) {
                menu.slotsChanged(player.getInventory());
            }
        }
    }
}
