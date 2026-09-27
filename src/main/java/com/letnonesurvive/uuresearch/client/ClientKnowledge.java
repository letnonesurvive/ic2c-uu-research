package com.letnonesurvive.uuresearch.client;

import com.letnonesurvive.uuresearch.research.KnowledgeSet;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Client-side copy of the world knowledge received from the server.
 */
public final class ClientKnowledge {

    private static final KnowledgeSet SET = new KnowledgeSet();
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile int version;

    private ClientKnowledge() {
    }

    public static void set(Collection<ResourceLocation> learned) {
        SET.replaceAll(learned);
        version++;
        LISTENERS.forEach(Runnable::run);
    }

    public static void clear() {
        set(List.of());
    }

    public static boolean isLearned(ResourceLocation id) {
        return SET.isLearned(id);
    }

    /** Increases on every update, so views can cheaply detect that they are stale. */
    public static int version() {
        return version;
    }

    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }
}
