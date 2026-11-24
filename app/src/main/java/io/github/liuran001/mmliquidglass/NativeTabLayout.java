package io.github.liuran001.mmliquidglass;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TabHost;
import android.widget.TabWidget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Keeps the native TabWidget indices stable while changing the on-screen order. */
final class NativeTabLayout {
    private static final String[] TAOBAO_TITLES = {"首页", "视频", "消息", "购物车", "我的淘宝"};
    private static final String[] ALIPAY_TITLES = {"首页", "理财", "生活", "消息", "我的"};
    private static final int ORDER_TAG = 0x7e534c54;
    private static final String TAOBAO_CELL = "com.taobao.tao.navigation.NavigationTabIndicatorView";

    private NativeTabLayout() {}

    static void applyTaobao(ViewGroup row) {
        if (!(row instanceof TabWidget) || row.getChildCount() != TAOBAO_TITLES.length) return;
        for (int index = 0; index < TAOBAO_TITLES.length; index++) {
            if (!TAOBAO_CELL.equals(row.getChildAt(index).getClass().getName())) return;
        }
        apply(row, TAOBAO_TITLES);
    }
    static void applyAlipay(ViewGroup row) {
        if (!(row instanceof TabWidget) || row.getChildCount() != ALIPAY_TITLES.length) return;
        View home = row.getChildAt(0);
        if (!"HomeTabWidgetItemView".equals(home.getClass().getSimpleName())) return;
        AlipayHomeBar.keepHomeLabelVisible(home);
        apply(row, ALIPAY_TITLES);
    }
    private static void apply(ViewGroup row, String[] titles) {
        if (GlassConfig.tabOrder.length == 0 && GlassConfig.tabHidden.length == 0) return;
        List<String> hidden = Arrays.asList(GlassConfig.tabHidden);
        List<Integer> order = new ArrayList<>(titles.length);
        order.add(0);
        for (String title : GlassConfig.tabOrder) {
            int index = Arrays.asList(titles).indexOf(title);
            if (index > 0 && !order.contains(index) && isVisible(index, titles, hidden)) {
                order.add(index);
            }
        }
        for (int index = 1; index < titles.length; index++) {
            if (!order.contains(index) && isVisible(index, titles, hidden)) order.add(index);
        }
        int[] slots = new int[order.size()];
        for (int slot = 0; slot < slots.length; slot++) slots[slot] = order.get(slot);
        row.setTag(ORDER_TAG, slots);
        for (int index = 0; index < titles.length; index++) {
            int visibility = order.contains(index) ? View.VISIBLE : View.GONE;
            View cell = row.getChildAt(index);
            if (cell.getVisibility() != visibility) cell.setVisibility(visibility);
        }
    }

    private static boolean isVisible(int index, String[] titles, List<String> hidden) {
        return index == titles.length - 1 || !hidden.contains(titles[index]);
    }

    private static int[] slots(ViewGroup row) {
        if (row == null) return null;
        Object order = row.getTag(ORDER_TAG);
        if (!(order instanceof int[])) return null;
        int[] slots = (int[]) order;
        for (int index : slots) {
            if (index >= row.getChildCount()) return null;
        }
        return slots;
    }

    static boolean hasOrder(ViewGroup row) {
        return slots(row) != null;
    }

    static void hideOrphanVideoPromotion(ViewGroup row, ViewGroup parent) {
        // Taobao mounts this promotion beside the TabHost rather than inside the video cell.
        if (!hasOrder(row) || slotForIndex(row, 1) >= 0) return;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View view = parent.getChildAt(i);
            if (view.getClass() != android.widget.FrameLayout.class
                    || view.getId() != View.NO_ID || view.getVisibility() != View.VISIBLE
                    || view.getTop() <= parent.getHeight() / 2
                    || view.getBottom() != parent.getHeight()) continue;
            ViewGroup overlay = (ViewGroup) view;
            if (overlay.getChildCount() != 2
                    || !(overlay.getChildAt(0) instanceof android.widget.ViewAnimator)
                    || overlay.getChildAt(1).getClass() != android.widget.FrameLayout.class) continue;
            ViewGroup images = (ViewGroup) overlay.getChildAt(0);
            ViewGroup badge = (ViewGroup) overlay.getChildAt(1);
            if (images.getChildCount() == 0 || badge.getChildCount() != 1
                    || !"com.taobao.uikit.extend.feature.view.TUrlImageView"
                            .equals(images.getChildAt(0).getClass().getName())
                    || !(badge.getChildAt(0) instanceof android.widget.ViewAnimator)) continue;
            ViewGroup labels = (ViewGroup) badge.getChildAt(0);
            if (labels.getChildCount() > 0 && labels.getChildAt(0) instanceof android.widget.TextView) {
                overlay.setVisibility(View.INVISIBLE);
            }
        }
    }

    static View tabAt(ViewGroup row, int slot) {
        int[] slots = slots(row);
        return slots != null && slot >= 0 && slot < slots.length ? row.getChildAt(slots[slot]) : null;
    }

    static int slotForIndex(ViewGroup row, int index) {
        int[] slots = slots(row);
        if (slots == null) return -1;
        for (int slot = 0; slot < slots.length; slot++) {
            if (slots[slot] == index) return slot;
        }
        return -1;
    }

    static int selectedSlot(ViewGroup row) {
        View ancestor = row;
        while (ancestor.getParent() instanceof View) {
            ancestor = (View) ancestor.getParent();
            if (ancestor instanceof TabHost) {
                return slotForIndex(row, ((TabHost) ancestor).getCurrentTab());
            }
        }
        return -1;
    }

    static void place(ViewGroup row) {
        int[] slots = slots(row);
        if (slots == null || row.isLayoutRequested()) return;
        // After native layout, move the actual view bounds so touch and the
        // droplet both see the same geometry. TabWidget still owns its children
        // at the original indices, including the original click listeners.
        int left = row.getPaddingLeft();
        for (int index : slots) {
            View tab = row.getChildAt(index);
            tab.offsetLeftAndRight(left - tab.getLeft());
            left += tab.getWidth();
        }
    }
}
