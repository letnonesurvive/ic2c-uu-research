package com.letnonesurvive.uuresearch.client;

/**
 * Timing of the "recipe not researched" message drawn over container screens: fully visible, then fading out.
 */
public final class LockedMessage {

    private static final long DURATION_MS = 4000;
    private static final long FADE_MS = 1000;

    private static long shownAt = -1;

    private LockedMessage() {
    }

    public static void show(long nowMs) {
        shownAt = nowMs;
    }

    /** Milliseconds since the message was shown, or -1 if it never was. */
    public static long elapsed(long nowMs) {
        return shownAt < 0 ? -1 : nowMs - shownAt;
    }

    /** Opacity for the given time since showing: 1 until the fade starts, then linearly down to 0. */
    public static float alpha(long elapsedMs) {
        if (elapsedMs < 0 || elapsedMs >= DURATION_MS) {
            return 0.0F;
        }
        long fadeStart = DURATION_MS - FADE_MS;
        return elapsedMs <= fadeStart ? 1.0F : (float) (DURATION_MS - elapsedMs) / FADE_MS;
    }
}
