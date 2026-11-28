package io.github.liuran001.mmliquidglass;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

/** Finds page-owned dock chrome independently of the floating bar and its lens. */
final class QqPageChrome {
    private final Rect viewport = new Rect();
    private final Rect bounds = new Rect();
    private View candidate;
    private boolean ambiguous;
    private View hidden;
    private ViewGroup content;
    private ViewGroup paddedPager;
    private int originalBottomPadding;
    private float originalAlpha;

    void sync(ViewGroup page, int dockHeight) {
        candidate = null;
        content = null;
        ambiguous = false;
        if (page != null && dockHeight > 0 && page.getGlobalVisibleRect(viewport)
                && viewport.height() > dockHeight * 3) {
            find(page, dockHeight);
        }
        View next = ambiguous ? null : candidate;
        candidate = null;
        if (hidden != next) {
            clear();
            hidden = next;
            if (next != null) {
                originalAlpha = next.getAlpha();
                LiquidGlassModule.log(android.util.Log.INFO, "QQ: suppressing page-owned dock chrome, height="
                        + next.getHeight());
            }
        }
        if (next != null && content != null) {
            reclaimPagerReserve(content, dockHeight, 0);
        }
        content = null;
        if (hidden != null && hidden.getAlpha() != 0f) {
            originalAlpha = hidden.getAlpha();
            hidden.setAlpha(0f);
        }
    }

    void clear() {
        if (hidden != null && hidden.getAlpha() == 0f) hidden.setAlpha(originalAlpha);
        hidden = null;
        restorePagerReserve();
    }

    private void restorePagerReserve() {
        if (paddedPager != null && paddedPager.getPaddingBottom() == 0) {
            paddedPager.setPadding(paddedPager.getPaddingLeft(), paddedPager.getPaddingTop(),
                    paddedPager.getPaddingRight(), originalBottomPadding);
        }
        paddedPager = null;
    }

    private boolean reclaimPagerReserve(ViewGroup root, int dockHeight, int depth) {
        if (depth > 10) return false;
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (!(child instanceof ViewGroup group) || !child.isShown()
                    || child.getHeight() <= dockHeight * 3
                    || child.getWidth() < viewport.width() * 0.9f) continue;
            if ("androidx.viewpager2.widget.ViewPager2".equals(root.getClass().getName())
                    && root.getChildCount() == 1) {
                if (root == paddedPager && root.getPaddingBottom() == 0) return true;
                int bottom = root.getPaddingBottom();
                if (Math.abs(bottom - dockHeight) > 2
                        || Math.abs(root.getHeight() - child.getBottom() - bottom) > 2) continue;
                restorePagerReserve();
                paddedPager = root;
                originalBottomPadding = bottom;
                // Keep native scroll/content spacing. Only the pager viewport
                // must reach the edge behind the floating pill.
                root.setPadding(root.getPaddingLeft(), root.getPaddingTop(), root.getPaddingRight(), 0);
                LiquidGlassModule.log(android.util.Log.INFO,
                        "QQ: reclaimed page pager bottom reserve, height=" + bottom);
                return true;
            }
            if (reclaimPagerReserve(group, dockHeight, depth + 1)) return true;
        }
        return false;
    }

    private void find(View view, int dockHeight) {
        if (ambiguous || view.getVisibility() != View.VISIBLE || !view.getGlobalVisibleRect(bounds)) return;
        int slop = Math.max(2, Math.round(dockHeight * 0.08f));
        if (Math.abs(bounds.bottom - viewport.bottom) > slop
                || bounds.width() < viewport.width() - slop) return;
        if (view instanceof ViewGroup group) {
            if (bounds.height() <= dockHeight + slop) return;
            for (int i = 0; i < group.getChildCount(); i++) find(group.getChildAt(i), dockHeight);
            return;
        }
        if (view.getClass() != View.class || view.isClickable() || view.isFocusable()
                || view.getContentDescription() != null
                || Math.abs(bounds.height() - dockHeight) > slop
                || Math.abs(view.getHeight() - dockHeight) > slop
                || !overlaysPageContent(view, slop)) return;
        if (candidate != null) ambiguous = true;
        else candidate = view;
    }

    private boolean overlaysPageContent(View strip, int slop) {
        if (!(strip.getParent() instanceof ViewGroup parent) || parent.getHeight() < strip.getHeight() * 3) {
            return false;
        }
        // A decorative strip must overlay a substantial content sibling; a
        // footer that reserves its own space must not be mistaken for chrome.
        for (int i = 0; i < parent.indexOfChild(strip); i++) {
            View peer = parent.getChildAt(i);
            if (peer instanceof ViewGroup && peer.isShown() && peer.getGlobalVisibleRect(bounds)
                    && bounds.width() >= viewport.width() - slop
                    && bounds.bottom >= viewport.bottom - slop
                    && bounds.height() > strip.getHeight() * 3) {
                if (candidate == null) content = (ViewGroup) peer;
                return true;
            }
        }
        return false;
    }
}
