package io.github.liuran001.mmliquidglass;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.view.Gravity;
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
    private LiquidGlassPanel glass;
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
        if (glass != null && glass.getParent() != bar) removeGlass();
        ViewGroup currentBackdrop = findBackdrop(section);
        if (currentBackdrop != backdrop) removeGlass();
        backdrop = currentBackdrop;
        if (row.getOrientation() != LinearLayout.HORIZONTAL || visibleChildren(row) != 5
                || !row.isShown() || row.getHeight() <= 0 || row.getWidth() <= 0) {
            removeGlass();
            restorePlayer();
            return;
        }

        float density = root.getResources().getDisplayMetrics().density;
        int inset = Math.round(8 * density);
        int height = row.getHeight() - row.getPaddingTop() - row.getPaddingBottom();
        int width = row.getWidth() - 2 * inset;
        if (height > 0 && width > 0) {
            if (glass == null) {
                boolean night = (root.getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                glass = new LiquidGlassPanel(bar.getContext(), backdrop, density, night);
                glass.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                bar.addView(glass, bar.indexOfChild(row), new FrameLayout.LayoutParams(0, 0));
            }
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) glass.getLayoutParams();
            int top = row.getTop() + row.getPaddingTop() - bar.getPaddingTop();
            int left = row.getLeft() + inset - bar.getPaddingLeft();
            if (lp.width != width || lp.height != height || lp.topMargin != top || lp.leftMargin != left) {
                lp.width = width;
                lp.height = height;
                lp.gravity = Gravity.TOP | Gravity.LEFT;
                lp.leftMargin = left;
                lp.topMargin = top;
                glass.setLayoutParams(lp);
            }
        } else {
            removeGlass();
        }
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

    private void removeGlass() {
        if (glass != null && glass.getParent() instanceof ViewGroup) {
            ((ViewGroup) glass.getParent()).removeView(glass);
        }
        glass = null;
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
            if (child.getVisibility() == View.VISIBLE && child.getWidth() > 0
                    && isNativeTab(child)) count++;
        }
        return count;
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
