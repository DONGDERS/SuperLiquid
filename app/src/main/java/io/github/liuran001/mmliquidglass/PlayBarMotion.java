package io.github.liuran001.mmliquidglass;

/** Capsule motion independent of Play's native icon and navigation animations. */
final class PlayBarMotion {
    private static final int COUNT = 5;
    private final PlayTabSelection selection = new PlayTabSelection();
    private final Spring position = new Spring(1f, 1000f, 0.001f, 0f);
    private final Spring scale = new Spring(0.72f, 250f, 0.001f, 1f);
    private boolean initialized;
    private boolean dragging;
    private boolean release;

    void observeNative(int index, long now) {
        selection.observeNative(index, COUNT);
        sync(now);
    }

    void press() {
        if (!initialized) return;
        release = false;
        scale.animateTo(1.39f);
    }

    void dragTo(float slot) {
        if (!initialized || !Float.isFinite(slot)) return;
        dragging = true;
        position.animateTo(Math.max(0f, Math.min(COUNT - 1f, slot)));
    }

    /** Call before performing the native action; false means no action is needed. */
    boolean releaseTo(int slot, long now) {
        if (!initialized || !dragging) {
            cancel(now);
            return false;
        }
        dragging = false;
        release = true;
        boolean click = selection.beginDragCommit(slot, COUNT, now);
        sync(now);
        return click;
    }

    void cancel(long now) {
        dragging = false;
        release = true;
        selection.clearPending();
        sync(now);
    }

    void reset() {
        selection.reset();
        initialized = false;
        dragging = false;
        release = false;
        position.snapTo(0f);
        scale.snapTo(1f);
    }

    boolean step(float dt, long now) {
        sync(now);
        boolean moving = position.update(dt);
        if (release && Math.abs(position.target() - position.value()) <= 0.1f) {
            release = false;
            scale.animateTo(1f);
        }
        return scale.update(dt) || moving;
    }

    boolean isReady() { return initialized; }
    float slot() { return position.value(); }
    float targetSlot() { return position.target(); }
    float scale() { return scale.value(); }
    long deadlineMs() { return selection.deadlineMs(); }

    private void sync(long now) {
        if (dragging) return;
        int display = selection.displayIndex(now);
        if (display == PlayTabSelection.UNKNOWN) return;
        if (initialized) position.animateTo(display);
        else {
            initialized = true;
            position.snapTo(display);
        }
    }
}