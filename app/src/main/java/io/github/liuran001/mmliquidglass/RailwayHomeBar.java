package io.github.liuran001.mmliquidglass;

import android.app.Activity;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;

final class RailwayHomeBar {
    static final String KEY = "railway";
    private static final String BAR = "ticket_home_bottom_bar";
    private static final String PAGE = "ticket_home_content_container";
    private static final String DIVIDER = "ticket_home_bottom_bar_gray";
    private static final String TIPS = "home_item_tips_compose";
    private static final String HOME = "ticket_home_bottom_bar_ticket";
    private static final String PAGER = "androidx.viewpager2.widget.ViewPager2";
    private static java.lang.ref.WeakReference<Activity> pending = new java.lang.ref.WeakReference<>(null);
    private static java.lang.ref.WeakReference<LiquidGlassHostLayout> installed = new java.lang.ref.WeakReference<>(null);

    private static Runnable refreshConfig;

    static void refreshConfig() {
        if (refreshConfig != null) refreshConfig.run();
    }
    private RailwayHomeBar() {}

    static void onHomeResume(Activity activity) {
        LiquidGlassHostLayout old = installed.get();
        if (old != null && old.isAttachedToWindow()
                && old.getRootView() == activity.getWindow().getDecorView()) {
            if (!GlassConfig.enabled && old.getParent() instanceof ViewGroup parent) parent.removeView(old);
            else if (refreshConfig != null) refreshConfig.run();
            return;
        }
        if (!GlassConfig.enabled || pending.get() == activity) return;
        pending = new java.lang.ref.WeakReference<>(activity);
        View decor = activity.getWindow().getDecorView();
        decor.post(() -> probe(activity, decor, 0));
    }

    private static void probe(Activity activity, View decor, int attempt) {
        if (pending.get() != activity) return;
        try {
            if (GlassConfig.enabled && !activity.isFinishing() && !activity.isDestroyed()
                    && decor.isAttachedToWindow()) {
                if (!install(activity) && attempt < 30) {
                    decor.postDelayed(() -> probe(activity, decor, attempt + 1), 160);
                    return;
                }
            }
        } catch (Throwable t) {
            LiquidGlassModule.logErr("railway: install failed", t);
        }
        pending.clear();
    }

    private static int id(Activity activity, String name) {
        return activity.getResources().getIdentifier(name, "id", activity.getPackageName());
    }

    private static boolean install(Activity activity) {
        View nativeBar = activity.findViewById(id(activity, BAR));
        View nativePage = activity.findViewById(id(activity, PAGE));
        View line = activity.findViewById(id(activity, DIVIDER));
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout parent) || !(nativeBar instanceof RadioGroup bar)
                || !(nativePage instanceof ViewGroup page) || !PAGER.equals(page.getClass().getName())
                || !(bar.getParent() instanceof RelativeLayout frame) || frame.getParent() != parent
                || page.getParent() != frame || !bar.isShown() || bar.getChildCount() != 5
                || bar.getWidth() <= 0 || page.getHeight() <= 0) return false;
        int homeId = id(activity, HOME);
        int tipsId = id(activity, TIPS);
        if (homeId == 0 || !(bar.findViewById(homeId) instanceof RadioButton)) return false;
        View divider = line != null && line.getParent() == frame ? line : null;
        float stockAlpha = bar.getAlpha();
        float dividerAlpha = divider == null ? 0f : divider.getAlpha();
        int stockAccessibility = bar.getImportantForAccessibility();
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(activity);
        int tabBottom = 0;
        for (int i = 0; i < bar.getChildCount(); i++) {
            if (!(bar.getChildAt(i) instanceof RadioButton source)
                    || source.getWidth() <= 0 || source.getHeight() <= 0) return false;
            row.addView(new TabCopy(source), new LinearLayout.LayoutParams(-2, source.getHeight()));
            tabBottom = Math.max(tabBottom, source.getBottom());
        }
        int extraBottom = Math.max(0, frame.getHeight() - bar.getTop() - tabBottom);
        int[] position = new int[2];
        int[] framePosition = new int[2];
        FrameLayout.LayoutParams rowParams = new FrameLayout.LayoutParams(-1, -2,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        row.measure(View.MeasureSpec.makeMeasureSpec(parent.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        row.layout(0, 0, row.getMeasuredWidth(), row.getMeasuredHeight());
        LiquidGlassHostLayout host = null;
        boolean success = false;
        try {
            parent.addView(row, rowParams);
            RuntimeTabCatalog.watch(row, KEY);
            TabLayoutApplier.apply(row, KEY);
            LiquidGlassInstaller.install(row, page);
            if (!(row.getParent() instanceof LiquidGlassHostLayout glass)) return false;
            host = glass;
            Runnable applyConfig = () -> {
                TabLayoutApplier.apply(row, KEY);
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) glass.getLayoutParams();
                int width = LiquidGlassInstaller.hugContentWidth(row, density, parent.getWidth());
                if (width > 0) params.width = width + glass.shadowPad() * 2;
                // Anchor the visible pill, not its transparent shadow padding.
                params.bottomMargin = extraBottom + Math.round(density * GlassConfig.barOffsetDp)
                        - glass.shadowPad();
                glass.setLayoutParams(params);
                glass.setTranslationX(Math.round(density * GlassConfig.barOffsetXDp));
                glass.setScaleX(GlassConfig.pillScale);
                glass.setScaleY(GlassConfig.pillScale);
                for (int i = 0; i < glass.getChildCount(); i++) glass.getChildAt(i).invalidate();
                glass.invalidate();
            };
            glass.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
                glass.setPivotX((r - l) / 2f);
                glass.setPivotY(b - t - glass.shadowPad());
            });
            applyConfig.run();
            ViewTreeObserver observer = glass.getViewTreeObserver();
            PageReserve reserve = new PageReserve();
            TipsLift lift = new TipsLift();
            ViewTreeObserver.OnPreDrawListener sync = () -> {
                boolean active = GlassConfig.enabled && bar.isShown() && bar.getParent() == frame
                        && page.getParent() == frame;
                glass.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
                bar.setAlpha(active ? 0f : stockAlpha);
                bar.setImportantForAccessibility(active ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        : stockAccessibility);
                if (divider != null) divider.setAlpha(active ? 0f : dividerAlpha);
                boolean changed = active ? reserve.expand(page, bar, divider, frame) : reserve.restore(page);
                int checked = bar.getCheckedRadioButtonId();
                boolean home = checked == homeId;
                // This build keeps offscreen UC WebViews attached to the pager.
                LiquidGlassInstaller.setMaterialOnly(glass, page, active && !home);
                View tips = active && home && tipsId != 0 ? page.findViewById(tipsId) : null;
                float offset = 0f;
                if (tips != null && tips.isShown()) {
                    tips.getLocationInWindow(position);
                    frame.getLocationInWindow(framePosition);
                    float naturalBottom = position[1] + tips.getHeight() - lift.appliedTo(tips);
                    offset = -Math.max(0f, naturalBottom - framePosition[1] - bar.getTop())
                            - Math.round(density * GlassConfig.railwayNoticeLiftDp);
                }
                lift.update(tips, offset);
                for (int i = 0; i < row.getChildCount(); i++) {
                    TabCopy copy = (TabCopy) row.getChildAt(i);
                    copy.sync();
                }
                return !changed;
            };
            observer.addOnPreDrawListener(sync);
            glass.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {}
                @Override public void onViewDetachedFromWindow(View v) {
                    if (observer.isAlive()) observer.removeOnPreDrawListener(sync);
                    if (refreshConfig == applyConfig) refreshConfig = null;
                    lift.update(null, 0);
                    reserve.restore(page);
                    LiquidGlassInstaller.setMaterialOnly(glass, page, false);
                    bar.setAlpha(stockAlpha);
                    bar.setImportantForAccessibility(stockAccessibility);
                    if (divider != null) divider.setAlpha(dividerAlpha);
                }
            });
            installed = new java.lang.ref.WeakReference<>(glass);
            refreshConfig = applyConfig;
            success = true;
            LiquidGlassModule.log(android.util.Log.INFO, "railway: native tabs mirrored");
            return true;
        } finally {
            if (!success) parent.removeView(host != null ? host : row);
        }
    }

    private static final class PageReserve {
        RelativeLayout.LayoutParams original;
        RelativeLayout.LayoutParams expanded;

        boolean expand(View page, RadioGroup bar, View divider, RelativeLayout frame) {
            if (page.getLayoutParams() == expanded
                    || !(page.getLayoutParams() instanceof RelativeLayout.LayoutParams lp)
                    || page.getBottom() >= frame.getHeight()) return false;
            int above = lp.getRule(RelativeLayout.ABOVE);
            boolean hasBarAnchor = above == bar.getId() || (divider != null && above == divider.getId());
            int reserve = frame.getHeight() - page.getBottom();
            boolean hasBarMargin = above == 0 && lp.bottomMargin > 0
                    && Math.abs(lp.bottomMargin - reserve) <= 1
                    && Math.abs(page.getBottom() - bar.getTop()) <= 1;
            if (!hasBarAnchor && !hasBarMargin) return false;
            original = lp;
            expanded = new RelativeLayout.LayoutParams(lp);
            expanded.removeRule(RelativeLayout.ABOVE);
            expanded.height = ViewGroup.LayoutParams.MATCH_PARENT;
            expanded.bottomMargin = 0;
            page.setLayoutParams(expanded);
            return true;
        }

        boolean restore(View page) {
            boolean ownsParams = expanded != null && page.getLayoutParams() == expanded;
            if (ownsParams) page.setLayoutParams(original);
            original = null;
            expanded = null;
            return ownsParams;
        }
    }

    private static final class TipsLift {
        View view;
        float base;
        float applied;

        float appliedTo(View target) {
            return view == target && view.getTranslationY() == base + applied ? applied : 0f;
        }

        void update(View target, float amount) {
            if (view != target) {
                if (view != null && view.getTranslationY() == base + applied) view.setTranslationY(base);
                view = target;
                applied = 0f;
                if (view != null) base = view.getTranslationY();
            }
            if (view == null) return;
            if (view.getTranslationY() != base + applied) base = view.getTranslationY();
            applied = amount;
            view.setTranslationY(base + applied);
        }
    }

    /** Read the native checked state, not the mirror from the preceding draw. */
    static int selectedSlot(ViewGroup row) {
        if (row == null) return -1;
        int slot = 0;
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;
            if (child instanceof TabCopy copy && copy.source.isChecked()) return slot;
            slot++;
        }
        return -1;
    }

    static final class TabCopy extends View {
        final RadioButton source;

        TabCopy(RadioButton source) {
            super(source.getContext());
            this.source = source;
            setOnClickListener(v -> {
                source.performClick();
                if (getParent() instanceof ViewGroup row) {
                    for (int i = 0; i < row.getChildCount(); i++) {
                        if (row.getChildAt(i) instanceof TabCopy copy) copy.sync();
                    }
                }
                LiquidGlassInstaller.requestSelectionSync();
            });
            sync();
        }

        void sync() {
            if (isSelected() != source.isChecked()) {
                setSelected(source.isChecked());
                // Native state changes don't dirty the mirror's display list.
                invalidate();
            }
            CharSequence label = source.getContentDescription();
            if (label == null) label = source.getText();
            if (!android.text.TextUtils.equals(getContentDescription(), label)) setContentDescription(label);
        }

        int contentWidth() {
            return Math.round(50f * getResources().getDisplayMetrics().density);
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(resolveSize(contentWidth(), widthSpec), resolveSize(source.getHeight(), heightSpec));
        }

        @Override protected void onDraw(Canvas canvas) {
            int save = canvas.save();
            canvas.translate((getWidth() - source.getWidth()) * .5f, (getHeight() - source.getHeight()) * .5f);
            source.draw(canvas);
            canvas.restoreToCount(save);
        }
    }
}
