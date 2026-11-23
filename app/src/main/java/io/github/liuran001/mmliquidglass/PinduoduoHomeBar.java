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
    private static final int TITLE = 0x7f09023d; // 7.96.0
    private static final int VERSION = 79600;
    private static java.lang.ref.WeakReference<Activity> pending = new java.lang.ref.WeakReference<>(null);
    private static java.lang.ref.WeakReference<LiquidGlassHostLayout> installed = new java.lang.ref.WeakReference<>(null);

    private PinduoduoHomeBar() {}

    static void onHomeResume(Activity activity) {
        if (!GlassConfig.enabled || pending.get() == activity) return;
        LiquidGlassHostLayout old = installed.get();
        if (old != null && old.isAttachedToWindow()
                && old.getRootView() == activity.getWindow().getDecorView()) return;
        try {
            if (activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0)
                    .versionCode != VERSION) return;
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            return;
        }
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
                    && child.findViewById(TITLE) instanceof TextView) tabs.add(child);
        }
        return tabs;
    }

    private static boolean install(Activity activity, ViewGroup bar) {
        java.util.ArrayList<View> tabs = tabs(bar);
        if (tabs.size() < 3 || tabs.size() > 6 || bar.getHeight() <= 0) return false;
        for (View tab : tabs) if (tab.getWidth() <= 0 || tab.getHeight() <= 0) return false;
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout) || ((FrameLayout) content).getChildCount() != 1) return false;
        FrameLayout parent = (FrameLayout) content;
        View root = parent.getChildAt(0);
        if (!(root instanceof ViewGroup)) return false;
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
            LiquidGlassInstaller.install(row, (ViewGroup) root);
            if (!(row.getParent() instanceof LiquidGlassHostLayout)) return false;
            host = (LiquidGlassHostLayout) row.getParent();
            final LiquidGlassHostLayout live = host;
            FrameLayout.LayoutParams glassLp = (FrameLayout.LayoutParams) host.getLayoutParams();
            glassLp.bottomMargin += reserve;
            host.setLayoutParams(glassLp);
            bar.setAlpha(0f);
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
                    if (observer.isAlive()) observer.removeOnPreDrawListener(sync);
                    live.removeCallbacks(refreshArt);
                    bar.setImportantForAccessibility(accessibility);
                }
            });
            installed = new java.lang.ref.WeakReference<>(host);
            return true;
        } finally {
            if (installed.get() != host || host == null) {
                View attached = row.getParent() instanceof LiquidGlassHostLayout
                        ? (View) row.getParent() : row;
                parent.removeView(attached);
                if (bar.getAlpha() != alpha) bar.setAlpha(alpha);
                bar.setImportantForAccessibility(accessibility);
            }
        }
    }

    private static final class TabCopy extends View {
        final View source;
        private final int naturalWidth;
        final TextView title;

        TabCopy(View source) {
            super(source.getContext());
            this.source = source;
            title = source.findViewById(TITLE);
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
