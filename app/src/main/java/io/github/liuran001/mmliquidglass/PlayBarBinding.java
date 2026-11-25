package io.github.liuran001.mmliquidglass;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;

/** Activity-scoped foreground, geometry and frame lifecycle for the Play bar. */
final class PlayBarBinding implements View.OnAttachStateChangeListener {
    private static final int TABS = 5;
    private final PlayHomeBar bridge;
    private final View decor;
    private final PlayBarMotion motion = new PlayBarMotion();
    private final LiquidGlassPanel plate;
    private final LiquidGlassPanel capsule;
    private boolean night;
    private final Choreographer choreographer = Choreographer.getInstance();
    private final Choreographer.FrameCallback frame = this::onFrame;
    private final Runnable timeout = this::requestFrame;
    private final android.graphics.RectF surfaceBounds = new android.graphics.RectF();
    private final int[] location = new int[2];
    private WeakReference<View> owner = new WeakReference<>(null);
    private PlayBarGesture gesture;
    private boolean scheduled;
    private boolean closed;
    private long lastFrame;
    private long scheduledDeadline;

    PlayBarBinding(Activity activity, PlayHomeBar bridge) {
        this.bridge = bridge;
        decor = activity.getWindow().getDecorView();
        night = isNight(decor);
        float density = decor.getResources().getDisplayMetrics().density;
        plate = LiquidGlassInstaller.installEmbedded(activity, density, night);
        capsule = plate == null ? null : LiquidGlassInstaller.installEmbedded(activity, density, night);
        decor.addOnAttachStateChangeListener(this);
        bridge.bind(this::observe, this::draw);
    }

    Object dispatch(View view, MotionEvent event, PlayBarGesture.NativeDispatch dispatch) throws Throwable {
        if (closed || view != owner.get() || gesture == null) return dispatch.dispatch(event);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                && (!view.hasWindowFocus() || !hasNativeIcons(view, surfaceBounds.left,
                surfaceBounds.top, surfaceBounds.width(), surfaceBounds.height()))) {
            gesture.clearRow();
        }
        return gesture.dispatch(event, dispatch);
    }

    private boolean draw(View view, Canvas canvas, PlayBackgroundNodes.Layer layer,
                         float x, float y, float width, float height) {
        if (closed || plate == null || capsule == null || !GlassConfig.enabled || !canvas.isHardwareAccelerated()
                || view.getRootView() != decor || !motion.isReady()) return false;
        float density = view.getResources().getDisplayMetrics().density;
        if (layer == PlayBackgroundNodes.Layer.SELECTED_INDICATOR) {
            return view == owner.get() && gesture != null
                    && width >= 24f * density - 1f
                    && width <= surfaceBounds.width() / TABS + density
                    && height >= 24f * density - 1f && height <= 40f * density
                    && x >= surfaceBounds.left - density
                    && x + width <= surfaceBounds.right + density
                    && y >= surfaceBounds.top - density
                    && y + height <= surfaceBounds.bottom + density
                    && hasNativeIcons(view, surfaceBounds.left, surfaceBounds.top,
                                      surfaceBounds.width(), surfaceBounds.height());
        }
        view.getLocationOnScreen(location);
        float bottom = location[1] + view.getHeight();
        if (Math.abs(width - view.getWidth()) > 2f * density
                || Math.abs(location[0] - x) > 2f * density
                || height < 44f * density || height > 112f * density
                || y + height > bottom + 2f * density || y + height < bottom - 36f * density
                || !hasNativeIcons(view, x, y, width, height)) return false;
        if (layer == PlayBackgroundNodes.Layer.RECTANGULAR_FILL) {
            // Keep this fill until the paired surface has actually drawn.
            return view == owner.get() && gesture != null
                    && Math.abs(x - surfaceBounds.left) <= density
                    && Math.abs(y - surfaceBounds.top) <= density
                    && Math.abs(width - surfaceBounds.width()) <= density
                    && Math.abs(height - surfaceBounds.height()) <= density;
        }
        View previous = owner.get();
        if (previous != view) {
            if (gesture != null) gesture.clearRow();
            owner = new WeakReference<>(view);
            gesture = new PlayBarGesture(view, motion, bridge::select, this::requestFrame);
            // Re-record the paired rectangular background outside this draw.
            decor.post(() -> { if (!closed) bridge.invalidate(view); });
        }
        surfaceBounds.set(x, y, x + width, y + height);
        gesture.setRow(x, y, x + width, y + height);
        if (night != isNight(view)) {
            night = isNight(view);
            plate.setTheme(night);
            capsule.setTheme(night);
        }
        float inset = Math.min(4f * density, height / 12f);
        float cell = width / TABS;
        float scale = motion.scale();
        int capsuleWidth = Math.round((cell - 2f * inset) * scale);
        int capsuleHeight = Math.round((height - 4f * inset) * scale);
        float capsuleX = (motion.slot() + .5f) * cell - capsuleWidth * .5f;
        float capsuleY = (height - capsuleHeight) * .5f;
        LiquidGlassPanel.EmbeddedBackdrop backdrop = bridge.backdrop(view);
        int save = canvas.save();
        try {
            canvas.translate(0f, inset);
            plate.drawEmbeddedMaterial(canvas, Math.round(width), Math.round(height - 2f * inset),
                    x, y + inset, backdrop);
        } finally {
            canvas.restoreToCount(save);
        }
        save = canvas.save();
        try {
            canvas.translate(capsuleX, capsuleY);
            capsule.setInteraction(Math.max(0f, Math.min(1f, (scale - 1f) / .39f)), capsuleWidth * .5f);
            capsule.drawEmbeddedMaterial(canvas, capsuleWidth, capsuleHeight,
                    x + capsuleX, y + capsuleY, backdrop);
        } finally {
            canvas.restoreToCount(save);
        }
        return true;
    }
    private static boolean isNight(View view) {
        return (view.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }
    private boolean hasNativeIcons(View view, float left, float top, float width, float height) {
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup compose = (ViewGroup) view;
        for (int i = 0; i < compose.getChildCount(); i++) {
            View child = compose.getChildAt(i);
            if (!(child instanceof ViewGroup) || !"kbs".equals(child.getClass().getName())) continue;
            ViewGroup icons = (ViewGroup) child;
            int cells = 0;
            icons.getLocationOnScreen(location);
            int x = location[0];
            int y = location[1];
            float bottom = top + height;
            if (width <= 0) return false;
            for (int j = 0; j < icons.getChildCount(); j++) {
                View icon = icons.getChildAt(j);
                if (!"leu".equals(icon.getClass().getName()) || !icon.isShown()
                        || icon.getWidth() <= 0 || icon.getHeight() <= 0) continue;
                float cx = x + (icon.getLeft() + icon.getRight()) * .5f;
                float cy = y + (icon.getTop() + icon.getBottom()) * .5f;
                if (cy < top || cy >= bottom) continue;
                int cell = (int) Math.floor((cx - left) * TABS / width);
                if (cell >= 0 && cell < TABS) cells |= 1 << cell;
            }
            return cells == (1 << TABS) - 1;
        }
        return false;
    }

    private void observe(int index) {
        if (closed) return;
        motion.observeNative(index, SystemClock.uptimeMillis());
        requestFrame();
    }

    private void requestFrame() {
        if (closed || scheduled) return;
        scheduled = true;
        choreographer.postFrameCallback(frame);
    }

    private void onFrame(long time) {
        scheduled = false;
        if (closed) return;
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0L ? 1f / 60f : Math.max(0f, (time - lastFrame) * 1e-9f);
        View view = owner.get();
        if (view != null && gesture != null && (!view.isShown() || !hasNativeIcons(view,
                surfaceBounds.left, surfaceBounds.top, surfaceBounds.width(), surfaceBounds.height()))) {
            gesture.clearRow();
        }
        boolean running = motion.step(dt, now);
        bridge.invalidate(view);
        lastFrame = running ? time : 0L;
        if (running) requestFrame();
        long deadline = motion.deadlineMs();
        if (deadline != scheduledDeadline) {
            decor.removeCallbacks(timeout);
            scheduledDeadline = deadline;
            if (deadline > now) decor.postDelayed(timeout, deadline - now);
        }
    }

    boolean owns(View view) { return !closed && view.getRootView() == decor; }

    void close() {
        if (closed) return;
        closed = true;
        choreographer.removeFrameCallback(frame);
        decor.removeCallbacks(timeout);
        decor.removeOnAttachStateChangeListener(this);
        bridge.unbind(this);
        if (gesture != null) gesture.clearRow();
        motion.reset();
    }

    @Override public void onViewAttachedToWindow(View view) {}
    @Override public void onViewDetachedFromWindow(View view) { close(); }
}