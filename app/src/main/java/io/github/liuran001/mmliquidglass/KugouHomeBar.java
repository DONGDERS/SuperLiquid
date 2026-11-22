package io.github.liuran001.mmliquidglass;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;

import java.lang.reflect.Method;
import java.util.WeakHashMap;

/** Locates Kugou's native bottom stack and hands it to the shared glass installer. */
final class KugouHomeBar implements ViewTreeObserver.OnGlobalLayoutListener,
        View.OnAttachStateChangeListener {
    private static final String PREFIX = "com.kugou.android.app.additionalui.";
    private static final String BAR = PREFIX + "playingbar.BottomTabView";
    private static final String ROW = PREFIX + "playingbar.BottomMainTabLayout";
    private static final String PLAYER = PREFIX + "playingbar.PlayingBarRootLayout";
    private static final String SECTION = PREFIX + "AdditionalLayout";
    private static final String PAGER = "com.kugou.common.base.MainFragmentViewPage";
    private static final WeakHashMap<View, Boolean> WATCHED = new WeakHashMap<>();
    private static Method currentType;

    private final View root;
    private ViewTreeObserver observer;
    private View player;
    private LinearLayout.LayoutParams liftedParams;
    private int nativeMargin;
    private int appliedMargin;

    private KugouHomeBar(View root) {
        this.root = root;
    }

    static void onHomeResume(Activity activity) {
        if (!GlassConfig.enabled) return;
        View root = activity.findViewById(android.R.id.content);
        if (root == null || WATCHED.put(root, true) != null) return;
        KugouHomeBar bridge = new KugouHomeBar(root);
        root.addOnAttachStateChangeListener(bridge);
        if (root.isAttachedToWindow()) bridge.onViewAttachedToWindow(root);
    }

    @Override public void onViewAttachedToWindow(View view) {
        observer = view.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(this);
        view.post(this::onGlobalLayout);
    }

    @Override public void onViewDetachedFromWindow(View view) {
        if (observer != null && observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
        observer = null;
        restorePlayer();
        player = null;
    }

    @Override public void onGlobalLayout() {
        if (observer == null) return;
        try {
            update();
        } catch (RuntimeException e) {
            onViewDetachedFromWindow(root);
            LiquidGlassModule.logErr("kugou bottom bar", e);
        }
    }

    private void update() {
        View section = findShown(root, SECTION);
        View bar = findShown(section, BAR);
        if (!(bar instanceof ViewGroup) || bar.getWidth() <= 0 || bar.getHeight() <= 0) {
            restorePlayer();
            return;
        }
        ViewGroup row = tabRow((ViewGroup) bar);
        if (!(row instanceof LinearLayout) || row.getHeight() <= 0
                || ((LinearLayout) row).getOrientation() != LinearLayout.HORIZONTAL
                || TabBarBridge.tabCount(row) < 2) {
            restorePlayer();
            return;
        }
        if (bar.getParent() instanceof LinearLayout
                && ((LinearLayout) bar.getParent()).getOrientation() == LinearLayout.VERTICAL) {
            View pager = findShown(root, PAGER);
            if (pager instanceof ViewGroup && pager.getWidth() > 0 && pager.getHeight() > 0) {
                LiquidGlassInstaller.install((ViewGroup) bar, (ViewGroup) pager);
            }
        }
        if (!(bar.getParent() instanceof LiquidGlassHostLayout)) {
            restorePlayer();
            return;
        }
        View nextPlayer = findShown(section, PLAYER);
        if (player != nextPlayer) {
            restorePlayer();
            player = nextPlayer;
        }
        float density = root.getResources().getDisplayMetrics().density;
        updatePlayer((View) bar.getParent(), Math.round(GlassConfig.kugouPlayerLiftDp * density));
    }

    static ViewGroup tabRow(ViewGroup bar) {
        View row = findShown(bar, ROW);
        return row instanceof ViewGroup && row.getParent() == bar ? (ViewGroup) row : null;
    }

    static int selectedSlot(ViewGroup row) {
        if (!(row.getParent() instanceof View)) return -1;
        View bar = (View) row.getParent();
        try {
            if (currentType == null || !currentType.getDeclaringClass().isInstance(bar)) {
                currentType = bar.getClass().getMethod("getCurrentType");
            }
            Object type = currentType.invoke(bar);
            if (type == null) return -1;
            for (int i = 0, n = TabBarBridge.tabCount(row); i < n; i++) {
                View tab = TabBarBridge.tabAt(row, i);
                if (tab != null && tab.getVisibility() == View.VISIBLE && type.equals(tab.getTag())) {
                    return i;
                }
            }
        } catch (ReflectiveOperationException ignored) { }
        return -1;
    }

    private void updatePlayer(View host, int lift) {
        if (lift <= 0 || player == null || !(player.getParent() instanceof LinearLayout)
                || !(player.getLayoutParams() instanceof LinearLayout.LayoutParams)) {
            restorePlayer();
            return;
        }
        LinearLayout stack = (LinearLayout) player.getParent();
        if (host.getParent() != stack || stack.getOrientation() != LinearLayout.VERTICAL
                || stack.indexOfChild(player) >= stack.indexOfChild(host)) {
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

    private static View findShown(View view, String className) {
        if (view == null || view.getVisibility() != View.VISIBLE) return null;
        if (className.equals(view.getClass().getName())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View match = findShown(group.getChildAt(i), className);
                if (match != null) return match;
            }
        }
        return null;
    }
}
