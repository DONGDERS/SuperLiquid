package io.github.liuran001.mmliquidglass;

/** UI-thread selection state for one attached Play bottom bar. */
final class PlayTabSelection {
    static final int UNKNOWN = -1;
    private static final long ACK_TIMEOUT_MS = 700L;

    private int nativeIndex = UNKNOWN;
    private int pendingIndex = UNKNOWN;
    private long deadlineMs;

    /** Receives the selected index from Play's Compose model, not the view tree. */
    void observeNative(int index, int tabCount) {
        if (index < 0 || index >= tabCount) return;
        // The previous selection is routinely emitted again before navigation
        // commits. A different native selection (including the target) wins.
        if (index != nativeIndex) clearPending();
        nativeIndex = index;
    }

    /** Arm before dispatching the native click, which may acknowledge synchronously. */
    boolean beginDragCommit(int target, int tabCount, long nowMs) {
        if (nativeIndex < 0 || nativeIndex >= tabCount
                || target < 0 || target >= tabCount) {
            return false;
        }
        if (target == nativeIndex) {
            clearPending();
            return false;
        }
        pendingIndex = target;
        deadlineMs = nowMs + ACK_TIMEOUT_MS;
        return true;
    }

    int displayIndex(long nowMs) {
        if (pendingIndex != UNKNOWN && nowMs >= deadlineMs) clearPending();
        return pendingIndex != UNKNOWN ? pendingIndex : nativeIndex;
    }

    /** Zero when there is no pending click; otherwise schedule a redraw here. */
    long deadlineMs() {
        return deadlineMs;
    }

    void clearPending() {
        pendingIndex = UNKNOWN;
        deadlineMs = 0L;
    }

    /** Discard pending work and observations on detach or a changed tab topology. */
    void reset() {
        clearPending();
        nativeIndex = UNKNOWN;
    }
}
