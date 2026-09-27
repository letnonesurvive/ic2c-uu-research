package com.letnonesurvive.uuresearch.research;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Set of researched item ids. Mutators return whether the set changed.
 */
public final class KnowledgeSet {

    private final Set<ResourceLocation> learned = new LinkedHashSet<>();

    public boolean learn(ResourceLocation id) {
        return learned.add(id);
    }

    public boolean forget(ResourceLocation id) {
        return learned.remove(id);
    }

    public boolean isLearned(ResourceLocation id) {
        return learned.contains(id);
    }

    public boolean learnAll(Collection<ResourceLocation> ids) {
        return learned.addAll(ids);
    }

    public boolean clear() {
        boolean changed = !learned.isEmpty();
        learned.clear();
        return changed;
    }

    public void replaceAll(Collection<ResourceLocation> ids) {
        learned.clear();
        learned.addAll(ids);
    }

    public Set<ResourceLocation> view() {
        return Collections.unmodifiableSet(learned);
    }

    public ListTag toTag() {
        ListTag tag = new ListTag();
        for (ResourceLocation id : learned) {
            tag.add(StringTag.valueOf(id.toString()));
        }
        return tag;
    }

    /** Invalid ids are skipped so a damaged save never prevents the world from loading. */
    public static KnowledgeSet fromTag(ListTag tag) {
        KnowledgeSet set = new KnowledgeSet();
        for (int i = 0; i < tag.size(); i++) {
            if (tag.get(i).getId() != Tag.TAG_STRING) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(tag.getString(i));
            if (id != null) {
                set.learned.add(id);
            }
        }
        return set;
    }
}
