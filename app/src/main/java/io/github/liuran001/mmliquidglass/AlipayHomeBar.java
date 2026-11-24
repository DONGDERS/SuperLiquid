package io.github.liuran001.mmliquidglass;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TabHost;
import android.widget.TabWidget;

final class AlipayHomeBar {
    private static final int BAR = 0x45070020;
    private static final int BACKGROUND = 0x4507001f;

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

    static void hideChrome(View bar) {
        if (bar == null) return;
        View chrome = bar.findViewById(BACKGROUND);
        if (chrome != null && chrome.getParent() == bar && chrome.getAlpha() != 0f) {
            chrome.setAlpha(0f);
        }
    }
}
