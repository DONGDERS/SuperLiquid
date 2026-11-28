package io.github.liuran001.mmliquidglass;

import android.app.Activity;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

final class PinduoduoHomeBar {
    private static final String PREFIX = "com.xunmeng.pinduoduo.ui_home_activity.widget.tab.";
    private static final String FRAME = "com.xunmeng.pinduoduo.ui_home_activity.widget.MainFrameContainerView";
    private static java.lang.ref.WeakReference<Activity> pending = new java.lang.ref.WeakReference<>(null);
    private static java.lang.ref.WeakReference<LiquidGlassHostLayout> installed = new java.lang.ref.WeakReference<>(null);

    private PinduoduoHomeBar() {}

    static void onHomeResume(Activity activity) {
        if (!GlassConfig.enabled || !"com.xunmeng.pinduoduo".equals(activity.getPackageName())
                || pending.get() == activity) return;
        LiquidGlassHostLayout old = installed.get();
        if (old != null && old.isAttachedToWindow()
                && old.getRootView() == activity.getWindow().getDecorView()) return;
        pending = new java.lang.ref.WeakReference<>(activity);
        View decor = activity.getWindow().getDecorView();
        decor.post(() -> probe(activity, decor, 0));
    }

    private static void probe(Activity activity, View decor, int attempt) {
        if (pending.get() != activity) return;
        try {
            if (!activity.isDestroyed() && !activity.isFinishing() && GlassConfig.enabled
                    && decor.isAttachedToWindow()) {
                ViewGroup nativeBar = findBar(decor);
                if (nativeBar != null && install(activity, nativeBar)) {
                    pending.clear();
                    return;
                }
                if (attempt < 32) {
                    decor.postDelayed(() -> probe(activity, decor, attempt + 1), 160);
                    return;
                }
                LiquidGlassModule.log(android.util.Log.WARN, "pinduoduo: stock bar retained; no matching layout");
            }
        } catch (Throwable t) {
            LiquidGlassModule.logErr("pinduoduo: install failed", t);
        }
        pending.clear();
    }

    private static ViewGroup findBar(View view) {
        if (!(view instanceof ViewGroup) || !view.isShown()) return null;
        ViewGroup group = (ViewGroup) view;
        if ((PREFIX + "PddTabView").equals(group.getClass().getName())) return group;
        for (int i = 0; i < group.getChildCount(); i++) {
            ViewGroup found = findBar(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static java.util.ArrayList<View> tabs(ViewGroup bar) {
        java.util.ArrayList<View> tabs = new java.util.ArrayList<>();
        for (int i = 0; i < bar.getChildCount(); i++) {
            View child = bar.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            if ((PREFIX + "TabRelativeLayout").equals(child.getClass().getName())
                    && titleView(child) != null) tabs.add(child);
        }
        return tabs;
    }

    private static TextView titleView(View view) {
        return RuntimeTabCatalog.titleView(view);
    }

    /**
     * Pinduoduo's coupon page uses MeCox's parallel WebView hierarchy rather
     * than android.webkit.WebView. Sampling that page's display list causes a
     * visible relit/flicker loop, so the page must use the material-only path.
     */
    private static boolean needsMaterialOnly(ViewGroup page) {
        return findWebViewLike(page);
    }

    private static boolean findWebViewLike(View view) {
        if (isWebViewLike(view) && view.isShown()
                && view.getWidth() > 0 && view.getHeight() > 0) return true;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child != view && findWebViewLike(child)) return true;
            }
        }
        return false;
    }

    private static boolean isWebViewLike(View view) {
        if (view instanceof android.webkit.WebView) return true;
        // The coupon page uses MeCox's WebView-compatible class. Walk the
        // superclass chain because PDD exposes it through OverFlingWebView.
        for (Class<?> type = view.getClass(); type != null; type = type.getSuperclass()) {
            if ("mecox.webkit.WebView".equals(type.getName())) return true;
        }
        return false;
    }

    private static ViewGroup findPage(ViewGroup frame, ViewGroup bar) {
        ViewGroup best = null;
        long area = 0;
        for (int i=0; i<frame.getChildCount(); i++) {
            View child = frame.getChildAt(i);
            if (child == bar || !(child instanceof ViewGroup candidate) || !child.isShown()
                    || child.getWidth() <= 0 || child.getHeight() <= 0) continue;
            long next = (long) child.getWidth() * child.getHeight();
            if (next > area) { best = candidate; area = next; }
        }
        return best;
    }

    // The dock can include a promotional header above its actual tab row.
    // Anchor the divider to the cells, not the outer bar or a build's resource ID.
    private static View findDivider(ViewGroup frame, ViewGroup bar, java.util.List<View> tabs) {
        int edge = Integer.MAX_VALUE;
        for (View tab : tabs) edge = Math.min(edge, bar.getTop() + tab.getTop());
        int tolerance = Math.max(2, Math.round(2 * frame.getResources().getDisplayMetrics().density));
        View match = null;
        for (int i = 0; i < frame.getChildCount(); i++) {
            View child = frame.getChildAt(i);
            if (child.getClass() != View.class || child.getVisibility() != View.VISIBLE
                    || child.isClickable() || child.getHeight() <= 0 || child.getHeight() > tolerance
                    || child.getWidth() < bar.getWidth() * 0.9f
                    || Math.abs(child.getLeft() - bar.getLeft()) > tolerance
                    || Math.abs(child.getRight() - bar.getRight()) > tolerance
                    || Math.abs(child.getTop() - edge) > tolerance) continue;
            if (match != null) return null;
            match = child;
        }
        return match;
    }

    private static final class DockDivider {
        private View held;
        private float alpha;

        void sync(View next) {
            if (held != next) {
                if (held != null && held.getAlpha() == 0f) held.setAlpha(alpha);
                held = next;
                if (next != null) alpha = next.getAlpha();
            }
            if (next != null && next.getAlpha() != 0f) {
                alpha = next.getAlpha();
                next.setAlpha(0f);
            }
        }
    }

    private static final class ContentReserve {
        private ViewGroup.MarginLayoutParams expanded;
        private int originalHeight, originalBottomMargin, appliedHeight, appliedBottomMargin;

        void remove(ViewGroup frame, ViewGroup page, View bar, View divider) {
            if (page.getParent() != frame || divider == null || divider.getParent() != frame) return;
            int edge = frame.getHeight() - frame.getPaddingBottom();
            int gap = edge - page.getBottom();
            int tolerance = Math.max(2, divider.getHeight());
            if (gap <= 0 || Math.abs(page.getBottom() - divider.getTop()) > tolerance
                    || divider.getTop() < bar.getTop() || divider.getTop() >= bar.getBottom()
                    || page.getWidth() < bar.getWidth() * 0.9f
                    || page.getHeight() <= bar.getHeight()
                    || !(page.getLayoutParams() instanceof ViewGroup.MarginLayoutParams lp)) return;
            if (lp == expanded && lp.height == appliedHeight && lp.bottomMargin == appliedBottomMargin) return;
            int nextHeight = lp.height;
            int nextMargin = lp.bottomMargin;
            if (lp.bottomMargin > 0 && Math.abs(lp.bottomMargin - gap) <= tolerance) {
                nextMargin = 0;
            } else if (lp.height > 0 && Math.abs(lp.height - page.getHeight()) <= tolerance
                    && lp.bottomMargin == 0) {
                // A fixed native page extent is rewritten on selection. Extend
                // from the actual top so the status bar inset remains untouched.
                nextHeight = edge - page.getTop();
            } else {
                return;
            }
            expanded = lp;
            originalHeight = lp.height;
            originalBottomMargin = lp.bottomMargin;
            appliedHeight = nextHeight;
            appliedBottomMargin = nextMargin;
            lp.height = nextHeight;
            lp.bottomMargin = nextMargin;
            page.setLayoutParams(lp);
        }

        void restore(ViewGroup page) {
            if (expanded != null && page.getLayoutParams() == expanded
                    && expanded.height == appliedHeight && expanded.bottomMargin == appliedBottomMargin) {
                expanded.height = originalHeight;
                expanded.bottomMargin = originalBottomMargin;
                page.setLayoutParams(expanded);
            }
            expanded = null;
        }
    }

    private static boolean install(Activity activity, ViewGroup bar) {
        java.util.ArrayList<View> tabs = tabs(bar);
        if (tabs.size() < 2 || tabs.size() > TabCatalogRules.MAX_TABS || bar.getHeight() <= 0) return false;
        for (View tab : tabs) if (tab.getWidth() <= 0 || tab.getHeight() <= 0) return false;
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout) || !(bar.getParent() instanceof ViewGroup)) return false;
        FrameLayout parent = (FrameLayout) content;
        ViewGroup frame = (ViewGroup) bar.getParent();
        if (!FRAME.equals(frame.getClass().getName()) || frame.getParent() != parent) return false;
        ViewGroup backdrop = findPage(frame, bar);
        if (backdrop == null) return false;
        DockDivider dockDivider = new DockDivider();
        ContentReserve contentReserve = new ContentReserve();
        int[] location = new int[2];
        int[] origin = new int[2];
        bar.getLocationInWindow(location);
        parent.getLocationInWindow(origin);
        int reserve = Math.max(0, parent.getHeight() - (location[1] - origin[1] + bar.getHeight()));
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (View tab : tabs) {
            View copy = new TabCopy(tab);
            row.addView(copy, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, tab.getHeight()));
        }
        RuntimeTabCatalog.watch(row, "pinduoduo");
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        row.measure(View.MeasureSpec.makeMeasureSpec(parent.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        row.layout(0, 0, row.getMeasuredWidth(), row.getMeasuredHeight());
        LiquidGlassHostLayout host = null;
        float alpha = bar.getAlpha();
        int accessibility = bar.getImportantForAccessibility();
        try {
            parent.addView(row, lp);
            TabLayoutApplier.apply(row, "pinduoduo");
            LiquidGlassInstaller.install(row, backdrop);
            if (!(row.getParent() instanceof LiquidGlassHostLayout)) return false;
            host = (LiquidGlassHostLayout) row.getParent();
            final LiquidGlassHostLayout live = host;
            LiquidGlassInstaller.setConfigBottomReserve(host, reserve);
            LiquidGlassInstaller.setMaterialOnly(host, backdrop, needsMaterialOnly(backdrop));
            bar.setAlpha(0f);
            dockDivider.sync(findDivider(frame, bar, tabs));
            bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            ViewTreeObserver observer = host.getViewTreeObserver();
            ViewTreeObserver.OnPreDrawListener sync = () -> {
                if (!live.isAttachedToWindow()) return true;
                if (!GlassConfig.enabled || !bar.isAttachedToWindow() || !tabs.equals(tabs(bar))) {
                    live.post(() -> {
                        if (live.getParent() == parent) parent.removeView(live);
                        onHomeResume(activity);
                    });
                    return true;
                }
                boolean visible = bar.isShown();
                live.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
                if (visible && bar.getAlpha() != 0f) bar.setAlpha(0f);
                View divider = visible ? findDivider(frame, bar, tabs) : null;
                dockDivider.sync(divider);
                if (visible && divider != null) {
                    contentReserve.remove(frame, backdrop, bar, divider);
                } else {
                    contentReserve.restore(backdrop);
                }
                LiquidGlassInstaller.setMaterialOnly(live, backdrop, needsMaterialOnly(backdrop));
                for (int i = 0; i < row.getChildCount(); i++) ((TabCopy) row.getChildAt(i)).sync();
                return true;
            };
            observer.addOnPreDrawListener(sync);
            // The native bar remains laid out but its invalidations do not dirty
            // the copies' display lists. Refresh live icons and badges when visible.
            Runnable refreshArt = new Runnable() {
                @Override public void run() {
                    if (!live.isAttachedToWindow()) return;
                    if (live.isShown() && live.hasWindowFocus()) {
                        for (int i = 0; i < row.getChildCount(); i++) row.getChildAt(i).invalidate();
                    }
                    live.postDelayed(this, 125);
                }
            };
            live.post(refreshArt);
            host.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {}
                @Override public void onViewDetachedFromWindow(View v) {
                    bar.setAlpha(alpha);
                    dockDivider.sync(null);
                    contentReserve.restore(backdrop);
                    LiquidGlassInstaller.setMaterialOnly(live, backdrop, false);
                    if (observer.isAlive()) observer.removeOnPreDrawListener(sync);
                    live.removeCallbacks(refreshArt);
                    bar.setImportantForAccessibility(accessibility);
                }
            });
            installed = new java.lang.ref.WeakReference<>(host);
            LiquidGlassModule.log(android.util.Log.INFO,
                    "pinduoduo: mirror installed, tabs=" + tabs.size() + " reserve=" + reserve);
            return true;
        } finally {
            if (installed.get() != host || host == null) {
                View attached = row.getParent() instanceof LiquidGlassHostLayout
                        ? (View) row.getParent() : row;
                parent.removeView(attached);
                if (bar.getAlpha() != alpha) bar.setAlpha(alpha);
                dockDivider.sync(null);
                if (host != null) LiquidGlassInstaller.setMaterialOnly(host, backdrop, false);
                bar.setImportantForAccessibility(accessibility);
            }
        }
    }

    static final class TabCopy extends View {
        final View source;
        private final int naturalWidth;
        final TextView title;

        TabCopy(View source) {
            super(source.getContext());
            this.source = source;
            title = titleView(source);
            naturalWidth = Math.round(44f * getResources().getDisplayMetrics().density);
            setOnClickListener(v -> {
                if (!source.performClick() && source instanceof ViewGroup) {
                    // This build may forward taps through a dedicated child.
                    ViewGroup tab = (ViewGroup) source;
                    for (int i = 0; i < tab.getChildCount(); i++) {
                        View child = tab.getChildAt(i);
                        if (child.getClass() == View.class && child.hasOnClickListeners()
                                && child.performClick()) break;
                    }
                }
            });
            sync();
        }

        String layoutTitle() {
            return title.getText().toString().trim();
        }

        void sync() {
            if (isSelected() != title.isSelected()) setSelected(title.isSelected());
            CharSequence label = source.getContentDescription() != null
                    ? source.getContentDescription() : title.getText();
            if (!android.text.TextUtils.equals(getContentDescription(), label)) {
                setContentDescription(label);
            }
            if (title.isSelected()) HostApp.PINDUODUO.accentOverride = title.getCurrentTextColor();

        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(resolveSize(naturalWidth, widthSpec),
                    resolveSize(source.getHeight(), heightSpec));
        }

        @Override protected void onDraw(Canvas canvas) {
            int save = canvas.save();
            canvas.translate((getWidth() - source.getWidth()) * 0.5f,
                    (getHeight() - source.getHeight()) * 0.5f);
            source.draw(canvas);
            canvas.restoreToCount(save);
        }
    }
}
