package io.github.liuran001.mmliquidglass;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import java.lang.ref.WeakReference;

final class AlipayHomeBar {
    private static final int BAR = 0x45070020;
    private static final int BACKGROUND = 0x4507001f;
    private static final int SELECTED_ICON_CONTAINER = 0x4507001d;
    private static final int SELECTED_ICON_SIZE_DP = 36;
    private static final int TAB_ICON = 0x45070023;
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

    private static boolean sHomeSizeHooked;
    private static WeakReference<View> sExpandedContent = new WeakReference<>(null);
    private static FrameLayout.LayoutParams sDockedContentParams;

    static void hookHomeIconSizing(View home) {
        if (sHomeSizeHooked) return;
        sHomeSizeHooked = true;
        try {
            LiquidGlassModule.hookAfter(home.getClass().getMethod("updateSelectLayoutSize", boolean.class),
                    chain -> {
                        if (!(chain.getThisObject() instanceof View)) return;
                        View cell = (View) chain.getThisObject();
                        if (!(cell.getParent() instanceof TabWidget) || !cell.isSelected()) return;
                        ViewGroup row = (ViewGroup) cell.getParent();
                        if (!(row.getParent() instanceof View)) return;
                        View bar = (View) row.getParent();
                        if (bar.getId() == BAR && bar.getParent() instanceof LiquidGlassHostLayout) {
                            keepSelectedHomeIconStill(row, cell);
                        }
                    });
        } catch (ReflectiveOperationException | RuntimeException e) {
            LiquidGlassModule.logErr("Alipay home icon sizing hook unavailable", e);
        }
    }

    static void extendSecondaryPage(ViewGroup content) {
        if (content == null || content.getId() != android.R.id.tabcontent
                || !(content.getParent() instanceof TabHost)) return;
        if (sExpandedContent.get() != content) {
            sExpandedContent = new WeakReference<>(content);
            sDockedContentParams = null;
        }
        int tab = ((TabHost) content.getParent()).getCurrentTab();
        if (!(content.getLayoutParams() instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams current = (FrameLayout.LayoutParams) content.getLayoutParams();
        boolean fullHeight = current.height == ViewGroup.LayoutParams.MATCH_PARENT
                && current.bottomMargin == 0;
        // Logical indices, unchanged by the floating bar's visual order.
        if (tab != 1 && tab != 3 && tab != 4) {
            if (fullHeight && sDockedContentParams != null) {
                content.setLayoutParams(sDockedContentParams);
            }
            sDockedContentParams = null;
            return;
        }
        if (fullHeight) return;
        sDockedContentParams = new FrameLayout.LayoutParams(current);
        FrameLayout.LayoutParams expanded = new FrameLayout.LayoutParams(current);
        expanded.height = ViewGroup.LayoutParams.MATCH_PARENT;
        expanded.bottomMargin = 0;
        content.setLayoutParams(expanded);
    }

    static boolean keepSelectedHomeIconStill(ViewGroup row, View home) {
        if (!home.isSelected()) return false;
        View selected = home.findViewById(SELECTED_ICON_CONTAINER);
        if (!(selected instanceof FrameLayout) || selected.getParent() != home
                || selected.getVisibility() != View.VISIBLE) return false;
        ViewGroup.LayoutParams lp = selected.getLayoutParams();
        if (lp == null) return false;
        // The native home feed switches selected_fl between 36dp and 50dp.
        // Keep its compact size without replacing the icon or its layout rules.
        int size = Math.round(SELECTED_ICON_SIZE_DP * selected.getResources().getDisplayMetrics().density);
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
        float offset = peerIconCenterY(row, home) - selected.getHeight() * 0.5f
                - selected.getTop();
        if (selected.getTranslationY() != offset) selected.setTranslationY(offset);
        FrameLayout indicator = (FrameLayout) selected;
        for (int i = 0; i < indicator.getChildCount(); i++) {
            View icon = indicator.getChildAt(i);
            if (icon instanceof ImageView && icon.getVisibility() == View.VISIBLE
                    && icon.getTranslationY() != 0f) icon.setTranslationY(0f);
        }
        return relayout && (selected.getWidth() != size || selected.getHeight() != size);
    }

    private static float peerIconCenterY(ViewGroup row, View home) {
        for (int i = 1; i < row.getChildCount(); i++) {
            View cell = row.getChildAt(i);
            if (cell.getVisibility() != View.VISIBLE || cell.isSelected()) continue;
            View icon = cell.findViewById(TAB_ICON);
            if (icon == null || icon.getParent() != cell
                    || icon.getVisibility() != View.VISIBLE || icon.getHeight() <= 0) continue;
            return cell.getTop() - home.getTop() + icon.getTop() + icon.getHeight() * 0.5f;
        }
        // The native compact row puts the peer icon center 16dp below its top.
        return 16f * home.getResources().getDisplayMetrics().density;
    }

    static void hideChrome(View bar) {
        if (bar == null) return;
        View chrome = bar.findViewById(BACKGROUND);
        if (chrome != null && chrome.getParent() == bar && chrome.getAlpha() != 0f) {
            chrome.setAlpha(0f);
        }
    }
}
