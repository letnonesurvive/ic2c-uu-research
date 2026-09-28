package com.letnonesurvive.uuresearch.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LockedMessageTest {

    @Test
    void fullyVisibleBeforeFade() {
        assertEquals(1.0F, LockedMessage.alpha(0));
        assertEquals(1.0F, LockedMessage.alpha(3000));
    }

    @Test
    void fadesOutDuringLastSecond() {
        assertEquals(0.5F, LockedMessage.alpha(3500), 0.001F);
    }

    @Test
    void goneAfterDuration() {
        assertEquals(0.0F, LockedMessage.alpha(4000));
        assertEquals(0.0F, LockedMessage.alpha(10_000));
    }

    @Test
    void neverShownIsInvisible() {
        assertEquals(0.0F, LockedMessage.alpha(-1));
    }
}
