package io.github.liuran001.mmliquidglass;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;


final class AlipayHomeBar {
    private static final java.util.Map<View, CharSequence> HOME_LABELS = new java.util.WeakHashMap<>();

    private AlipayHomeBar() { }

    static ViewGroup findBar(View root) {
        View bar = findBarInTree(root);
        if (!validBar(bar)) return null;
        return backdrop((ViewGroup) bar.getParent()) != null ? (ViewGroup) bar : null;
    }

    private static boolean validBar(View bar) {
        if (!(bar instanceof FrameLayout) || !(bar.getParent() instanceof TabHost)
                || !bar.isShown() || bar.getWidth() <= 0) return false;
        View tabs = bar.findViewById(android.R.id.tabs);
        if (!(tabs instanceof TabWidget) || tabs.getParent() != bar || !tabs.isShown()
                || tabs.getHeight() <= 0) return false;
        int count = ((TabWidget) tabs).getChildCount();
        return count >= 2 && count <= TabCatalogRules.MAX_TABS;
    }

    private static View findBarInTree(View view) {
        if (validBar(view)) return view;
        if (view instanceof ViewGroup group) {
            for (int i=0; i<group.getChildCount(); i++) {
                View match = findBarInTree(group.getChildAt(i));
                if (match != null) return match;
            }
        }
        return null;
    }

    static ViewGroup backdrop(ViewGroup parent) {
        if (!(parent instanceof TabHost)) return null;
        View content = ((TabHost) parent).getTabContentView();
        return content instanceof ViewGroup && content.getParent() == parent
                && content.getHeight() > 0 ? (ViewGroup) content : null;
    }

    private static <T extends View> T childWithRole(View parent, String role, Class<T> type) {
        if (!(parent instanceof ViewGroup group)) return null;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (!type.isInstance(child) || child.getId() == View.NO_ID) continue;
            try {
                if (role.equals(child.getResources().getResourceEntryName(child.getId()))) {
                    return type.cast(child);
                }
            } catch (android.content.res.Resources.NotFoundException ignored) { }
        }
        return null;
    }

    static void keepHomeLabelVisible(View home) {
        TextView description = childWithRole(home, "tab_description", TextView.class);
        if (description == null) return;
        CharSequence current = description.getText();
        if (!TextUtils.isEmpty(current)) HOME_LABELS.put(home, current.toString());
        CharSequence learned = HOME_LABELS.get(home);
        if (TextUtils.isEmpty(current) && learned != null) description.setText(learned);
        if (description.getVisibility() != View.VISIBLE) description.setVisibility(View.VISIBLE);
        if (description.getAlpha() != 1f) description.setAlpha(1f);
    }

    /** Native TabHost reintroduces its docked-bar reserve during page switches. */
    static boolean extendSecondaryPage(ViewGroup content) {
        if (content == null || content.getId() != android.R.id.tabcontent
                || !(content.getParent() instanceof TabHost tabs)
                || tabs.getCurrentTab() < 0) return false;
        if (!(content.getLayoutParams() instanceof FrameLayout.LayoutParams current)) return false;
        if (current.height == ViewGroup.LayoutParams.MATCH_PARENT && current.bottomMargin == 0) return false;
        // The floating bar overlays every tab, including home. Never restore
        // a docked snapshot on return: that is precisely the revived gap.
        FrameLayout.LayoutParams expanded = new FrameLayout.LayoutParams(current);
        expanded.height = ViewGroup.LayoutParams.MATCH_PARENT;
        expanded.bottomMargin = 0;
        content.setLayoutParams(expanded);
        return true;
    }

    static boolean keepSelectedHomeIconStill(ViewGroup row, View home) {
        if (!home.isSelected()) return false;
        FrameLayout selected = childWithRole(home, "selected_fl", FrameLayout.class);
        ImageView peer = visiblePeerIcon(row, home);
        if (selected == null || selected.getVisibility() != View.VISIBLE || peer == null) return false;
        ViewGroup.LayoutParams lp = selected.getLayoutParams();
        if (lp == null) return false;
        // Take the compact dimensions and vertical alignment from the live row.
        int size = Math.min(peer.getWidth(), peer.getHeight());
        boolean relayout = lp.width != size || lp.height != size;
        if (relayout) {
            lp.width = size;
            lp.height = size;
            selected.setLayoutParams(lp);
        }
        // Native animators write these after layout. Correct the rendered
        // properties in pre-draw, scoped to this selected home indicator.
        if (selected.getScaleX() != 1f) selected.setScaleX(1f);
        if (selected.getScaleY() != 1f) selected.setScaleY(1f);
        View peerCell = (View) peer.getParent();
        float peerCenter = peerCell.getTop() - home.getTop() + peer.getTop() + peer.getHeight() * 0.5f;
        float offset = peerCenter - selected.getHeight() * 0.5f - selected.getTop();
        if (selected.getTranslationY() != offset) selected.setTranslationY(offset);
        for (int i = 0; i < selected.getChildCount(); i++) {
            View icon = selected.getChildAt(i);
            if (icon instanceof ImageView && icon.getVisibility() == View.VISIBLE
                    && icon.getTranslationY() != 0f) icon.setTranslationY(0f);
        }
        return relayout && (selected.getWidth() != size || selected.getHeight() != size);
    }

    private static ImageView visiblePeerIcon(ViewGroup row, View home) {
        for (int i = 1; i < row.getChildCount(); i++) {
            View cell = row.getChildAt(i);
            if (cell.getVisibility() != View.VISIBLE || cell.isSelected()) continue;
            ImageView icon = childWithRole(cell, "tab_icon", ImageView.class);
            if (icon != null && icon.getVisibility() == View.VISIBLE
                    && icon.getWidth() > 0 && icon.getHeight() > 0) return icon;
        }
        return null;
    }

    static void hideChrome(View bar) {
        View chrome = childWithRole(bar, "tab_bar_bg_iv", View.class);
        if (chrome != null && chrome.getAlpha() != 0f) {
            chrome.setAlpha(0f);
        }
    }
}
