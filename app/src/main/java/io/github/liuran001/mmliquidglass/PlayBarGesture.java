package io.github.liuran001.mmliquidglass;

import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/** Leaves taps/vertical gestures native; takes over only a horizontal capsule drag. */
final class PlayBarGesture {
    interface NativeDispatch {
        private boolean rowMoved() {
        return Math.abs(activeRow.left - row.left) > 1f || Math.abs(activeRow.top - row.top) > 1f
                || Math.abs(activeRow.right - row.right) > 1f || Math.abs(activeRow.bottom - row.bottom) > 1f;
    }

    Object dispatch(MotionEvent event) throws Throwable;
    }

    interface Commit {
        boolean select(int index);
    }

    private final int slop;
    private final PlayBarMotion motion;
    private final Commit commit;
    private final Runnable redraw;
    private final RectF row = new RectF();
    private final RectF activeRow = new RectF();
    private float downX;
    private float downY;
    private float downSlot;
    private int pointer = -1;
    private boolean claimed;

    PlayBarGesture(View owner, PlayBarMotion motion, Commit commit, Runnable redraw) {
        slop = ViewConfiguration.get(owner.getContext()).getScaledTouchSlop();
        this.motion = motion;
        this.commit = commit;
        this.redraw = redraw;
    }

    /** Screen-space bounds of the last successfully drawn native bottom row. */
    void setRow(float left, float top, float right, float bottom) {
        row.set(left, top, right, bottom);
    }

    void clearRow() {
        row.setEmpty();
        motion.cancel(SystemClock.uptimeMillis());
        pointer = -1;
        // If native already received CANCEL, swallow the rest of that stream.
    }

    private boolean rowMoved() {
        return Math.abs(activeRow.left - row.left) > 1f || Math.abs(activeRow.top - row.top) > 1f
                || Math.abs(activeRow.right - row.right) > 1f || Math.abs(activeRow.bottom - row.bottom) > 1f;
    }

    Object dispatch(MotionEvent event, NativeDispatch nativeDispatch) throws Throwable {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            claimed = false;
            pointer = -1;
            downX = event.getRawX();
            downY = event.getRawY();
            if (motion.isReady() && row.contains(downX, downY)) {
                float tabWidth = row.width() / 5f;
                float centerX = row.left + (motion.slot() + .5f) * tabWidth;
                if (Math.abs(downX - centerX) <= tabWidth * .5f) {
                    pointer = event.getPointerId(0);
                    downSlot = motion.slot();
                    activeRow.set(row);
                    motion.press();
                    redraw.run();
                }
            }
            return nativeDispatch.dispatch(event);
        }
        if (pointer < 0) {
            if (!claimed) return nativeDispatch.dispatch(event);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) claimed = false;
            return true;
        }
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN
                || event.getPointerId(0) != pointer || rowMoved()) {
            boolean swallow = claimed;
            pointer = -1;
            if (action == MotionEvent.ACTION_CANCEL) claimed = false;
            motion.cancel(now);
            redraw.run();
            return swallow ? Boolean.TRUE : nativeDispatch.dispatch(event);
        }
        if (action == MotionEvent.ACTION_MOVE) {
            float dx = event.getRawX() - downX;
            float dy = event.getRawY() - downY;
            if (!claimed && Math.abs(dy) > slop && Math.abs(dy) >= Math.abs(dx)) {
                pointer = -1;
                motion.cancel(now);
                redraw.run();
                return nativeDispatch.dispatch(event);
            }
            if (!claimed && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) {
                MotionEvent cancel = MotionEvent.obtain(event);
                try {
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    nativeDispatch.dispatch(cancel);
                } finally {
                    cancel.recycle();
                }
                claimed = true;
            }
            if (claimed) {
                motion.dragTo(downSlot + 5f * dx / activeRow.width());
                redraw.run();
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP) {
            pointer = -1;
            if (claimed) {
                claimed = false;
                motion.dragTo(downSlot + 5f * (event.getRawX() - downX) / activeRow.width());
                int target = Math.round(motion.targetSlot());
                if (motion.releaseTo(target, now) && !commit.select(target)) motion.cancel(now);
                redraw.run();
                return true;
            }
            motion.cancel(now);
            redraw.run();
        }
        return nativeDispatch.dispatch(event);
    }
}