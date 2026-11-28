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
    private float originalAlpha;

    void sync(ViewGroup page, int dockHeight) {
        candidate = null;
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
            if (next != null) originalAlpha = next.getAlpha();
        }
        if (hidden != null && hidden.getAlpha() != 0f) {
            originalAlpha = hidden.getAlpha();
            hidden.setAlpha(0f);
        }
    }

    void clear() {
        if (hidden != null && hidden.getAlpha() == 0f) hidden.setAlpha(originalAlpha);
        hidden = null;
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
                || (view.getBackground() == null && view.getForeground() == null)
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
                    && bounds.height() > strip.getHeight() * 3) return true;
        }
        return false;
    }
}
