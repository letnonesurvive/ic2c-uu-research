package com.letnonesurvive.uuresearch.research;

import com.letnonesurvive.uuresearch.UUResearch;
import com.letnonesurvive.uuresearch.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Collection;
import java.util.Set;

/**
 * World-wide research knowledge, stored with the overworld data.
 */
public final class ResearchKnowledge extends SavedData {

    private static final String NAME = UUResearch.MOD_ID + "_knowledge";
    private static final String TAG_LEARNED = "learned";

    private final KnowledgeSet set = new KnowledgeSet();

    public static ResearchKnowledge get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(ResearchKnowledge::load, ResearchKnowledge::new, NAME);
    }

    private static ResearchKnowledge load(CompoundTag tag) {
        ResearchKnowledge knowledge = new ResearchKnowledge();
        knowledge.set.replaceAll(KnowledgeSet.fromTag(tag.getList(TAG_LEARNED, Tag.TAG_STRING)).view());
        return knowledge;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put(TAG_LEARNED, set.toTag());
        return tag;
    }

    public boolean isLearned(ResourceLocation id) {
        return set.isLearned(id);
    }

    public Set<ResourceLocation> learned() {
        return set.view();
    }

    public boolean learn(ResourceLocation id) {
        return changed(set.learn(id));
    }

    public boolean forget(ResourceLocation id) {
        return changed(set.forget(id));
    }

    public boolean learnAll(Collection<ResourceLocation> ids) {
        return changed(set.learnAll(ids));
    }

    public boolean reset() {
        return changed(set.clear());
    }

    private boolean changed(boolean changed) {
        if (changed) {
            setDirty();
            ModNetwork.sendToAll(set.view());
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                CraftingGrids.refresh(server);
            }
        }
        return changed;
    }
}
