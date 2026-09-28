package com.letnonesurvive.uuresearch.research;

import com.letnonesurvive.uuresearch.network.ModNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tells the player who laid out an unresearched UU recipe why no result appears. The item is deliberately not
 * named: otherwise trying random patterns would reveal recipes without researching them.
 * <p>
 * The message waits until the blocked pattern has stayed unchanged for a while: while a player lays out a
 * pattern, the intermediate grids are often other (unresearched) UU recipes and would otherwise spam messages.
 */
public final class CraftLockNotifier {

    private static final long DELAY_TICKS = 40;
    // Server thread only; weak keys drop players that left or respawned
    private static final Map<ServerPlayer, Pending> PENDING = new WeakHashMap<>();

    private CraftLockNotifier() {
    }

    /** Tick the wait started at: kept while the same pattern stays blocked, restarted for a new pattern. */
    public static long waitStart(@Nullable Long previousStart, boolean samePattern, long now) {
        return previousStart != null && samePattern ? previousStart : now;
    }

    public static boolean isDue(long now, long waitStart) {
        return now - waitStart >= DELAY_TICKS;
    }

    /** Called when a pattern in this grid was blocked; remembers it for the player whose open menu shows the grid. */
    public static void onBlocked(Container grid, @Nullable Level level) {
        if (level == null || level.isClientSide || level.getServer() == null) {
            return;
        }
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (showsGrid(player, grid)) {
                List<ItemStack> contents = snapshot(grid);
                Pending previous = PENDING.get(player);
                boolean samePattern = previous != null && previous.grid == grid && sameContents(previous.contents, contents);
                long start = waitStart(previous == null ? null : previous.start, samePattern, level.getGameTime());
                PENDING.put(player, new Pending(grid, contents, start));
                return;
            }
        }
    }

    /** Sends due messages whose pattern is still in the grid; drops ones whose grid changed or was closed. */
    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        Iterator<Map.Entry<ServerPlayer, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ServerPlayer, Pending> entry = it.next();
            ServerPlayer player = entry.getKey();
            Pending pending = entry.getValue();
            if (player.hasDisconnected() || !showsGrid(player, pending.grid)
                    || !sameContents(pending.contents, snapshot(pending.grid))) {
                it.remove();
            } else if (isDue(now, pending.start)) {
                ModNetwork.sendRecipeLocked(player);
                it.remove();
            }
        }
    }

    /** Forgets waiting messages, e.g. when knowledge or the config changed and blocked patterns may now craft. */
    public static void clear() {
        PENDING.clear();
    }

    private static boolean showsGrid(ServerPlayer player, Container grid) {
        for (Slot slot : player.containerMenu.slots) {
            if (slot.container == grid) {
                return true;
            }
        }
        return false;
    }

    private static List<ItemStack> snapshot(Container grid) {
        List<ItemStack> contents = new ArrayList<>(grid.getContainerSize());
        for (int i = 0; i < grid.getContainerSize(); i++) {
            contents.add(grid.getItem(i).copy());
        }
        return contents;
    }

    private static boolean sameContents(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private record Pending(Container grid, List<ItemStack> contents, long start) {
    }
}
