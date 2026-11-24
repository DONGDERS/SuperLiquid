package io.github.liuran001.mmliquidglass;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

final class AlipayHomeBar {
    private static final int BAR = 0x45070020;
    private static final int BACKGROUND = 0x4507001f;
    private static final int SELECTED_ICON_CONTAINER = 0x4507001d;
    private static final int SELECTED_ICON_SIZE_DP = 36;
    private static final int TAB_DESCRIPTION = 0x45070022;
    private static final String HOME_LABEL = "首页";

    private AlipayHomeBar() { }

    static ViewGroup findBar(View root) {
        View bar = root.findViewById(BAR);
        if (!(bar instanceof FrameLayout) || !(bar.getParent() instanceof TabHost)
                || !bar.isShown() || bar.getWidth() <= 0) return null;
        View tabs = bar.findViewById(android.R.id.tabs);
        if (!(tabs instanceof TabWidget) || tabs.getParent() != bar || !tabs.isShown()
                || tabs.getHeight() <= 0 || ((TabWidget) tabs).getChildCount() != 5) return null;
        return backdrop((ViewGroup) bar.getParent()) != null ? (ViewGroup) bar : null;
    }

    static ViewGroup backdrop(ViewGroup parent) {
        if (!(parent instanceof TabHost)) return null;
        View content = ((TabHost) parent).getTabContentView();
        return content instanceof ViewGroup && content.getParent() == parent
                && content.getHeight() > 0 ? (ViewGroup) content : null;
    }

    static void keepHomeLabelVisible(View home) {
        View label = home.findViewById(TAB_DESCRIPTION);
        if (!(label instanceof TextView) || label.getParent() != home) return;
        TextView description = (TextView) label;
        // Alipay can hide or replace this label when switching tabs.
        if (!TextUtils.equals(HOME_LABEL, description.getText())) description.setText(HOME_LABEL);
        if (description.getVisibility() != View.VISIBLE) description.setVisibility(View.VISIBLE);
        if (description.getAlpha() != 1f) description.setAlpha(1f);
    }

    static void keepSelectedHomeIconSize(View home) {
        if (!home.isSelected()) return;
        View selected = home.findViewById(SELECTED_ICON_CONTAINER);
        if (!(selected instanceof FrameLayout) || selected.getParent() != home
                || selected.getVisibility() != View.VISIBLE) return;
        ViewGroup.LayoutParams lp = selected.getLayoutParams();
        if (lp == null) return;
        // The native home feed switches selected_fl between 36dp and 50dp.
        // Keep its compact size without replacing the icon or its layout rules.
        int size = Math.round(SELECTED_ICON_SIZE_DP * selected.getResources().getDisplayMetrics().density);
        if (lp.width != size || lp.height != size) {
            lp.width = size;
            lp.height = size;
            selected.setLayoutParams(lp);
        }
    }

    static void hideChrome(View bar) {
        if (bar == null) return;
        View chrome = bar.findViewById(BACKGROUND);
        if (chrome != null && chrome.getParent() == bar && chrome.getAlpha() != 0f) {
            chrome.setAlpha(0f);
        }
    }
}
