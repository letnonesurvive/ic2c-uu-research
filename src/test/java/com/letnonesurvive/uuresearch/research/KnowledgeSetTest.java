package com.letnonesurvive.uuresearch.research;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeSetTest {

    private static final ResourceLocation DIAMOND = new ResourceLocation("minecraft", "diamond");
    private static final ResourceLocation IRON = new ResourceLocation("minecraft", "iron_ingot");

    @Test
    void learnReportsChangeOnlyOnce() {
        KnowledgeSet set = new KnowledgeSet();
        assertTrue(set.learn(DIAMOND));
        assertFalse(set.learn(DIAMOND));
        assertTrue(set.isLearned(DIAMOND));
    }

    @Test
    void forgetAndClear() {
        KnowledgeSet set = new KnowledgeSet();
        set.learnAll(List.of(DIAMOND, IRON));
        assertTrue(set.forget(DIAMOND));
        assertFalse(set.forget(DIAMOND));
        assertTrue(set.clear());
        assertFalse(set.clear());
        assertTrue(set.view().isEmpty());
    }

    @Test
    void replaceAllOverwrites() {
        KnowledgeSet set = new KnowledgeSet();
        set.learn(DIAMOND);
        set.replaceAll(List.of(IRON));
        assertEquals(Set.of(IRON), set.view());
    }

    @Test
    void roundTripsThroughTag() {
        KnowledgeSet set = new KnowledgeSet();
        set.learnAll(List.of(DIAMOND, IRON));
        assertEquals(set.view(), KnowledgeSet.fromTag(set.toTag()).view());
    }

    @Test
    void fromTagSkipsInvalidIds() {
        ListTag tag = new ListTag();
        tag.add(StringTag.valueOf("minecraft:diamond"));
        tag.add(StringTag.valueOf("Not A Valid:Id!"));
        assertEquals(Set.of(DIAMOND), KnowledgeSet.fromTag(tag).view());
    }

    @Test
    void viewIsReadOnly() {
        KnowledgeSet set = new KnowledgeSet();
        assertThrows(UnsupportedOperationException.class, () -> set.view().add(DIAMOND));
    }
}
