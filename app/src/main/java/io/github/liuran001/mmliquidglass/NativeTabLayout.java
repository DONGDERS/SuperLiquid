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
    private static final int ORDER_TAG = 0x7e534c54;

    private NativeTabLayout() {}

    static void applyTaobao(ViewGroup row) {
        if (!(row instanceof TabWidget) || row.getChildCount() < 2 || row.getChildCount() > TabCatalogRules.MAX_TABS) return;
        apply(row, "taobao");
    }
    static void applyAlipay(ViewGroup row) {
        if (!(row instanceof TabWidget) || row.getChildCount() < 2 || row.getChildCount() > TabCatalogRules.MAX_TABS) return;
        View home = row.getChildAt(0);
        AlipayHomeBar.keepHomeLabelVisible(home);
        apply(row, "alipay");
    }
    private static void apply(ViewGroup row, String appKey) {
        GlassConfig.ensureHostVersion(row.getContext());
        RuntimeTabCatalog.observeRow(row, appKey);
        String[] titles = new String[row.getChildCount()];
        List<String> nativeKeys = new ArrayList<>();
        List<Integer> nativeIndices = new ArrayList<>();
        for (int i = 0; i < titles.length; i++) {
            View cell = row.getChildAt(i);
            titles[i] = RuntimeTabCatalog.layoutKey(appKey, cell);
            if (RuntimeTabCatalog.nativeVisibility(cell) == View.GONE) continue;
            if (titles[i].isEmpty()) { restore(row); return; }
            nativeKeys.add(titles[i]);
            nativeIndices.add(i);
        }
        if (new java.util.HashSet<>(nativeKeys).size() != nativeKeys.size()) {
            restore(row);
            return;
        }
        if (!GlassConfig.matchesTabCatalog(nativeKeys)
                || (GlassConfig.tabOrder.length == 0 && GlassConfig.tabHidden.length == 0)) {
            restore(row);
            return;
        }
        List<String> hidden = Arrays.asList(GlassConfig.tabHidden);
        List<Integer> order = new ArrayList<>(nativeKeys.size());
        int entry = nativeIndices.get(0);
        order.add(entry);
        for (String title : GlassConfig.tabOrder) {
            int index = Arrays.asList(titles).indexOf(title);
            if (index >= 0 && RuntimeTabCatalog.nativeVisibility(row.getChildAt(index)) != View.GONE
                    && index != entry && !order.contains(index)
                    && isVisible(appKey, titles[index], hidden)) {
                order.add(index);
            }
        }
        for (int index : nativeIndices) {
            if (!order.contains(index)
                    && isVisible(appKey, titles[index], hidden)) order.add(index);
        }
        int[] slots = new int[order.size()];
        for (int slot = 0; slot < slots.length; slot++) slots[slot] = order.get(slot);
        row.setTag(ORDER_TAG, slots);
        for (int index = 0; index < titles.length; index++) {
            int visibility = order.contains(index) ? View.VISIBLE : View.GONE;
            View cell = row.getChildAt(index);
            if (cell.getVisibility() != visibility) cell.setVisibility(visibility);
        }
        RuntimeTabCatalog.markApplied(row);
    }

    private static void restore(ViewGroup row) {
        boolean hadOrder = row.getTag(ORDER_TAG) != null;
        row.setTag(ORDER_TAG, null);
        RuntimeTabCatalog.restoreRow(row);
        if (hadOrder) row.requestLayout();
    }

    private static boolean isVisible(String appKey, String key, List<String> hidden) {
        return RuntimeTabCatalog.isLockedKey(appKey, key) || !hidden.contains(key);
    }

    private static int[] slots(ViewGroup row) {
        if (row == null) return null;
        Object order = row.getTag(ORDER_TAG);
        if (!(order instanceof int[])) return null;
        int[] slots = (int[]) order;
        if (slots.length != TabBarBridge.tabCount(row)) return null;
        for (int index : slots) {
            if (index < 0 || index >= row.getChildCount()) return null;
        }
        if (slots.length != TabBarBridge.tabCount(row)) return null;
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

    static int currentTabIndex(ViewGroup row) {
        View ancestor = row;
        while (ancestor.getParent() instanceof View) {
            ancestor = (View) ancestor.getParent();
            if (ancestor instanceof TabHost) return ((TabHost) ancestor).getCurrentTab();
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
