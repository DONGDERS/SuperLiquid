package io.github.liuran001.mmliquidglass;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.TextView;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** Kugou 20.5.3: dress the native navigation without moving its tabs. */
final class KugouHomeBar implements ViewTreeObserver.OnGlobalLayoutListener,
        View.OnAttachStateChangeListener {
    private static final String PREFIX = "com.kugou.android.app.additionalui.";
    private static final String BAR = PREFIX + "playingbar.BottomTabView";
    private static final String ROW = PREFIX + "playingbar.BottomMainTabLayout";
    private static final String TAB_ITEM = "com.kugou.common.base.bar.BaseBottomTabItemView";
    private static final String PLAYER = PREFIX + "playingbar.PlayingBarRootLayout";
    private static final String ADDITIONAL = PREFIX + "AdditionalLayout";
    private static final java.util.WeakHashMap<View, Boolean> WATCHED = new java.util.WeakHashMap<>();

    private final View root;
    private ViewTreeObserver observer;
    private LinearLayout row;
    private View section;
    private View player;
    private ViewGroup backdrop;
    private LiquidGlassHostLayout host;
    private DropletPanel selection;
    private FrameLayout boundBar;
    private Drawable rowBackground;
    private Drawable barBackground;
    private boolean nativeClipChildren;
    private boolean nativeClipToPadding;
    private final int[] barLocation = new int[2];
    private final Rect windowFrame = new Rect();

    private final Rect pillBounds = new Rect();
    private final ViewTreeObserver.OnPreDrawListener selectionObserver = () -> {
        updateSelection();
        return true;
    };
    private final Rect tabContents = new Rect();
    private LinearLayout.LayoutParams liftedParams;
    private int nativeMargin;
    private int appliedMargin;
    private final Rect rowBounds = new Rect();
    private final Rect contentBounds = new Rect();

    private KugouHomeBar(View root) {
        this.root = root;
    }

    static void onHomeResume(Activity activity) {
        if (!GlassConfig.enabled) return;
        View root = activity.findViewById(android.R.id.content);
        if (root == null || WATCHED.containsKey(root)) return;
        WATCHED.put(root, true);
        KugouHomeBar bridge = new KugouHomeBar(root);
        root.addOnAttachStateChangeListener(bridge);
        if (root.isAttachedToWindow()) bridge.onViewAttachedToWindow(root);
    }

    @Override public void onViewAttachedToWindow(View v) {
        observer = v.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(this);
        v.post(this::onGlobalLayout);
    }

    @Override public void onViewDetachedFromWindow(View v) {
        if (observer != null && observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
        observer = null;
        clearBinding();
    }

    @Override public void onGlobalLayout() {
        if (observer == null) return;
        try {
            update();
        } catch (RuntimeException e) {
            if (observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
            observer = null;
            clearBinding();
            LiquidGlassModule.logErr("kugou bottom bar", e);
        }
    }

    private void update() {
        if (row == null || !row.isAttachedToWindow() || !row.isShown()) {
            clearBinding();
            bind();
        }
        if (row == null || !(row.getParent() instanceof FrameLayout)) return;
        FrameLayout bar = (FrameLayout) row.getParent();
        if (player == null || !player.isAttachedToWindow() || !player.isShown()) {
            restorePlayer();
            player = findShown(section, PLAYER);
        }
        if (row.getOrientation() != LinearLayout.HORIZONTAL || visibleChildren(row) != 5
                || row.getHeight() <= 0 || row.getWidth() <= 0) {
            clearBinding();
            return;
        }
        float density = root.getResources().getDisplayMetrics().density;
        tabContents.setEmpty();
        int first = row.getWidth();
        int last = 0;
        for (int i = 0; i < row.getChildCount(); i++) {
            View tab = row.getChildAt(i);
            if (!isVisibleTab(tab)) continue;
            first = Math.min(first, tab.getLeft());
            last = Math.max(last, tab.getRight());
            collectTabContents(tab, tab.getLeft(), tab.getTop());
        }
        // Only fall back to the tab centres when a skin draws its labels itself.
        int naturalHeight = Math.round(48 * density);
        int centerY = (row.getPaddingTop() + row.getHeight() - row.getPaddingBottom()) / 2;
        if (!tabContents.isEmpty()) {
            centerY = tabContents.centerY();
            naturalHeight = Math.max(naturalHeight, tabContents.height() + Math.round(12 * density));
        }
        int height = Math.round(naturalHeight * clamp(GlassConfig.pillScale, 0.1f, 3f));
        int width = Math.round((last - first - 16 * density)
                * clamp(GlassConfig.widthScale, 0.5f, 1.2f));
        if (width <= 0 || height <= 0) return;
        ViewGroup currentBackdrop = findBackdrop(section);
        if (boundBar != bar || backdrop != currentBackdrop) {
            removeGlass();
            backdrop = currentBackdrop;
            installGlass(bar, density);
        }
        rowBackground = clearBackground(row, rowBackground);
        barBackground = clearBackground(bar, barBackground);
        // The shared 12dp setting maps to the native icon centre on Kugou.
        int center = row.getTop() + centerY
                + Math.round((12f - clamp(GlassConfig.barOffsetDp, 0f, 24f)) * density);
        bar.getLocationOnScreen(barLocation);
        bar.getWindowVisibleDisplayFrame(windowFrame);
        int gap = Math.round(6f * density);
        int limit = windowFrame.bottom - barLocation[1] - gap - Math.round(row.getTranslationY());
        // Keep the capsule centred on the icons; trim excess height before
        // moving its centre. The native row is never translated by the module.
        if (limit > center) height = Math.min(height, 2 * (limit - center));
        int left = row.getLeft() + (first + last - width) / 2
                + Math.round(clamp(GlassConfig.barOffsetXDp, -120f, 120f) * density);
        int top = Math.min(center - height / 2, limit - height);
        pillBounds.set(left, top, left + width, top + height);
        updatePlayer(bar, Math.round(GlassConfig.kugouPlayerLiftDp * density));
    }

    private void bind() {
        section = findShown(root, ADDITIONAL);
        if (section == null) return;
        View bar = findShown(section, BAR);
        if (!(bar instanceof FrameLayout)) return;
        FrameLayout nativeBar = (FrameLayout) bar;
        for (int i = 0; i < nativeBar.getChildCount(); i++) {
            View child = nativeBar.getChildAt(i);
            if (!(child instanceof LinearLayout) || !child.isShown()) continue;
            LinearLayout candidate = (LinearLayout) child;
            if (candidate.getOrientation() == LinearLayout.HORIZONTAL
                    && (ROW.equals(candidate.getClass().getName()) || visibleChildren(candidate) == 5)) {
                row = candidate;
                return;
            }
        }
    }

    private void updatePlayer(View bar, int lift) {
        if (lift <= 0 || player == null || !player.isShown()
                || !(player.getParent() instanceof LinearLayout)
                || !(player.getLayoutParams() instanceof LinearLayout.LayoutParams)) {
            restorePlayer();
            return;
        }
        LinearLayout stack = (LinearLayout) player.getParent();
        if (bar.getParent() != stack || stack.getOrientation() != LinearLayout.VERTICAL
                || stack.indexOfChild(player) >= stack.indexOfChild(bar)) {
            restorePlayer();
            return;
        }
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) player.getLayoutParams();
        if (lp != liftedParams || lp.bottomMargin != appliedMargin) nativeMargin = lp.bottomMargin;
        liftedParams = lp;
        appliedMargin = nativeMargin + lift;
        if (lp.bottomMargin != appliedMargin) {
            lp.bottomMargin = appliedMargin;
            player.setLayoutParams(lp);
        }
    }

    private void restorePlayer() {
        if (player != null && liftedParams != null && player.getLayoutParams() == liftedParams
                && liftedParams.bottomMargin == appliedMargin && appliedMargin != nativeMargin) {
            liftedParams.bottomMargin = nativeMargin;
            player.setLayoutParams(liftedParams);
        }
        liftedParams = null;
    }

    private void clearBinding() {
        removeGlass();
        restorePlayer();
        row = null;
        section = null;
        player = null;
        backdrop = null;
    }

    private void installGlass(FrameLayout bar, float density) {
        boundBar = bar;
        nativeClipChildren = bar.getClipChildren();
        nativeClipToPadding = bar.getClipToPadding();
        bar.setClipChildren(false);
        bar.setClipToPadding(false);
        host = new LiquidGlassHostLayout(bar.getContext(), backdrop, row);
        host.setupShadow(density, host.isDark());
        host.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        LiquidGlassPanel panel = new LiquidGlassPanel(bar.getContext(), backdrop, density, host.isDark());
        panel.useAppTuning();
        host.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        // Only draw the selected wash: native icon/text and touch stay above it.
        selection = new DropletPanel(bar.getContext(), backdrop, null, density, host.isDark());
        selection.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        selection.setVisibility(View.INVISIBLE);
        host.setGlassTuner(new LiquidGlassHostLayout.GlassTuner() {
            @Override public void onSize(int w, int h, float radius) { panel.invalidate(); }
            @Override public void onTheme(boolean dark) {
                panel.setTheme(dark);
                if (selection != null) selection.setTheme(dark);
            }
        });
        // Zero-sized layout params keep decorations out of native measurement.
        bar.addView(host, bar.indexOfChild(row), new FrameLayout.LayoutParams(0, 0));
        bar.addView(selection, bar.indexOfChild(row), new FrameLayout.LayoutParams(0, 0));
        host.attach(false);
        observer.addOnPreDrawListener(selectionObserver);
    }

    private void removeGlass() {
        if (boundBar == null) return;
        ViewTreeObserver tree = root.getViewTreeObserver();
        if (tree.isAlive()) tree.removeOnPreDrawListener(selectionObserver);
        if (row.getBackground() == null && rowBackground != null) row.setBackground(rowBackground);
        if (boundBar.getBackground() == null && barBackground != null) boundBar.setBackground(barBackground);
        boundBar.setClipChildren(nativeClipChildren);
        boundBar.setClipToPadding(nativeClipToPadding);
        boundBar.removeView(host);
        boundBar.removeView(selection);
        boundBar = null;
        host = null;
        selection = null;
        rowBackground = null;
        barBackground = null;
    }

    private void updateSelection() {
        if (selection == null || row == null) return;
        host.setTranslationX(row.getTranslationX());
        host.setTranslationY(row.getTranslationY());
        host.setAlpha(row.getAlpha());
        selection.setTranslationX(row.getTranslationX());
        selection.setTranslationY(row.getTranslationY());
        selection.setAlpha(row.getAlpha());
        host.setVisibility(row.isShown() ? View.VISIBLE : View.INVISIBLE);
        int pad = host.shadowPad();
        position(host, pillBounds.width() + 2 * pad, pillBounds.height() + 2 * pad,
                pillBounds.left - pad, pillBounds.top - pad);
        View tab = null;
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (isVisibleTab(child) && (child.isSelected() || child.isActivated())) {
                tab = child;
                break;
            }
        }
        if (tab == null || !row.isShown() || pillBounds.isEmpty()) {
            selection.setVisibility(View.INVISIBLE);
            return;
        }
        int left = Math.max(pillBounds.left, row.getLeft() + tab.getLeft());
        int right = Math.min(pillBounds.right, row.getLeft() + tab.getRight());
        if (right <= left) {
            selection.setVisibility(View.INVISIBLE);
            return;
        }
        position(selection, right - left, pillBounds.height(), left, pillBounds.top);
        selection.setVisibility(View.VISIBLE);
    }

    private void collectTabContents(View view, int x, int y) {
        if (view.getVisibility() != View.VISIBLE || view.getWidth() <= 0 || view.getHeight() <= 0) return;
        if (view instanceof ImageView || view instanceof TextView) {
            int left = x + view.getPaddingLeft();
            int top = y + view.getPaddingTop();
            int right = x + view.getWidth() - view.getPaddingRight();
            int bottom = y + view.getHeight() - view.getPaddingBottom();
            if (right > left && bottom > top) tabContents.union(left, top, right, bottom);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                collectTabContents(child, x + child.getLeft(), y + child.getTop());
            }
        }
    }

    private static Drawable clearBackground(View view, Drawable previous) {
        Drawable current = view.getBackground();
        if (current == null) return previous;
        int l = view.getPaddingLeft(), t = view.getPaddingTop();
        int r = view.getPaddingRight(), b = view.getPaddingBottom();
        view.setBackground(null);
        view.setPadding(l, t, r, b);
        return current;
    }

    private static float clamp(float value, float min, float max) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : 1f;
    }

    private static void position(View view, int width, int height, int left, int top) {
        if (view.isLayoutRequested() || view.getMeasuredWidth() != width || view.getMeasuredHeight() != height) {
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        }
        if (view.isLayoutRequested() || view.getLeft() != left || view.getTop() != top
                || view.getWidth() != width || view.getHeight() != height) {
            view.layout(left, top, left + width, top + height);
        }
    }

    private ViewGroup findBackdrop(View section) {
        if (!row.getGlobalVisibleRect(rowBounds)) return null;
        View branch = section;
        while (branch != root && branch.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) branch.getParent();
            for (int i = parent.indexOfChild(branch) - 1; i >= 0; i--) {
                View candidate = parent.getChildAt(i);
                if (candidate instanceof ViewGroup && candidate.isShown()
                        && candidate.getGlobalVisibleRect(contentBounds)
                        && Rect.intersects(rowBounds, contentBounds)) return (ViewGroup) candidate;
            }
            branch = parent;
        }
        return null; // Static surface when the native layout reserves its own bottom area.
    }

    private static int visibleChildren(ViewGroup group) {
        int count = 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (isVisibleTab(child)) count++;
        }
        return count;
    }

    private static boolean isVisibleTab(View tab) {
        return tab.getVisibility() == View.VISIBLE && tab.getWidth() > 0 && isNativeTab(tab);
    }

    private static boolean isNativeTab(View tab) {
        for (Class<?> type = tab.getClass(); type != null; type = type.getSuperclass()) {
            if (TAB_ITEM.equals(type.getName())) return true;
        }
        return false;
    }

    private static View findShown(View view, String name) {
        if (view.getVisibility() != View.VISIBLE) return null;
        if (name.equals(view.getClass().getName())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View result = findShown(group.getChildAt(i), name);
                if (result != null) return result;
            }
        }
        return null;
    }
}
