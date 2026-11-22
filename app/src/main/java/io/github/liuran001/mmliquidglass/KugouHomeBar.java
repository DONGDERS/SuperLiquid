package io.github.liuran001.mmliquidglass;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.TextView;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/** Kugou 20.5.3: dress the native navigation without moving its tabs. */
final class KugouHomeBar implements ViewTreeObserver.OnGlobalLayoutListener,
        View.OnAttachStateChangeListener {
    private static final String PREFIX = "com.kugou.android.app.additionalui.";
    private static final String BAR = PREFIX + "playingbar.BottomTabView";
    private static final String ROW = PREFIX + "playingbar.BottomMainTabLayout";
    private static final String TAB_ITEM = "com.kugou.common.base.bar.BaseBottomTabItemView";
    private static final String PLAYER = PREFIX + "playingbar.PlayingBarRootLayout";
    private static final String ADDITIONAL = PREFIX + "AdditionalLayout";
    private static final java.util.WeakHashMap<View, Boolean> WATCHED = new java.util.WeakHashMap<>();

    private static final WeakHashMap<View, WeakReference<KugouHomeBar>> BARS = new WeakHashMap<>();
    private static Method currentType;
    private DropletDragController drag;
    private LiquidGlassPanel pill;
    private boolean ownsTouch;
    private boolean trackingTouch;
    private int confirmed = -1;
    private long confirmAfter;
    private Set<View> configuredCells;
    private String[] tabOrder, tabHidden;
    private View[] nativeOrder;
    private int[] nativeVisibility;
    private final View root;

    static void install(ClassLoader loader) {
        try {
            Class<?> type = Class.forName(BAR, false, loader);
            currentType = type.getMethod("getCurrentType");
            LiquidGlassModule.hookIntercept(type.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class), chain -> {
                WeakReference<KugouHomeBar> ref = BARS.get(chain.getThisObject());
                KugouHomeBar live = ref == null ? null : ref.get();
                if (live == null || live.drag == null) return chain.proceed();
                MotionEvent event = (MotionEvent) chain.getArg(0);
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    live.ownsTouch = false;
                    live.trackingTouch = live.selection.isShown()
                            && event.getX() >= live.selection.getX()
                            && event.getX() <= live.selection.getX() + live.selection.getWidth()
                            && event.getY() >= live.selection.getY()
                            && event.getY() <= live.selection.getY() + live.selection.getHeight();
                }
                if (live.trackingTouch && !live.ownsTouch && live.drag.onIntercept(event)) {
                    live.ownsTouch = true;
                    int original = event.getAction();
                    try {
                        event.setAction(MotionEvent.ACTION_CANCEL);
                        chain.proceed();
                    } finally {
                        event.setAction(original);
                    }
                    if (live.boundBar != null && live.boundBar.getParent() != null) {
                        live.boundBar.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                if (live.ownsTouch && live.drag != null) {
                    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        if (live.boundBar.getParent() != null) {
                            live.boundBar.getParent().requestDisallowInterceptTouchEvent(false);
                        }
                        live.ownsTouch = live.trackingTouch = false;
                        live.confirmAfter = android.os.SystemClock.uptimeMillis() + 160;
                        live.root.postDelayed(live.root::invalidate, 160);
                    }
                    live.drag.onTouch(event);
                    return true;
                }
                return chain.proceed();
            });
        } catch (ReflectiveOperationException | RuntimeException e) {
            LiquidGlassModule.logErr("kugou touch bridge", e);
        }
    }
    private ViewTreeObserver observer;
    private LinearLayout row;
    private View section;
    private View player;
    private ViewGroup backdrop;
    private LiquidGlassHostLayout host;
    private DropletPanel selection;
    private FrameLayout boundBar;
    private Drawable rowBackground;
    private Drawable barBackground;
    private boolean nativeClipChildren;
    private boolean nativeClipToPadding;
    private final int[] barLocation = new int[2];
    private final Rect windowFrame = new Rect();

    private final Rect pillBounds = new Rect();
    private final ViewTreeObserver.OnPreDrawListener selectionObserver = () -> {
        updateSelection();
        return true;
    };
    private final Rect tabContents = new Rect();
    private LinearLayout.LayoutParams liftedParams;
    private int nativeMargin;
    private int appliedMargin;
    private final Rect rowBounds = new Rect();
    private final Rect contentBounds = new Rect();

    private KugouHomeBar(View root) {
        this.root = root;
    }

    static void onHomeResume(Activity activity) {
        if (!GlassConfig.enabled) return;
        View root = activity.findViewById(android.R.id.content);
        if (root == null || WATCHED.containsKey(root)) return;
        WATCHED.put(root, true);
        KugouHomeBar bridge = new KugouHomeBar(root);
        root.addOnAttachStateChangeListener(bridge);
        if (root.isAttachedToWindow()) bridge.onViewAttachedToWindow(root);
    }

    @Override public void onViewAttachedToWindow(View v) {
        observer = v.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(this);
        v.post(this::onGlobalLayout);
    }

    @Override public void onViewDetachedFromWindow(View v) {
        if (observer != null && observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
        observer = null;
        clearBinding();
    }

    @Override public void onGlobalLayout() {
        if (observer == null) return;
        try {
            update();
        } catch (RuntimeException e) {
            if (observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
            observer = null;
            clearBinding();
            LiquidGlassModule.logErr("kugou bottom bar", e);
        }
    }

    private void update() {
        if (row == null || !row.isAttachedToWindow() || !row.isShown()) {
            clearBinding();
            bind();
        }
        if (row == null || !(row.getParent() instanceof FrameLayout)) return;
        FrameLayout bar = (FrameLayout) row.getParent();
        if (player == null || !player.isAttachedToWindow() || !player.isShown()) {
            restorePlayer();
            player = findShown(section, PLAYER);
        }
        if (row.getOrientation() != LinearLayout.HORIZONTAL
                || row.getHeight() <= 0 || row.getWidth() <= 0) return;
        // The host can rebuild the row in place when its tab list changes.
        Set<View> cells = new HashSet<>();
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (isNativeTab(child)) cells.add(child);
        }
        if (cells.size() < 2 || cells.size() != row.getChildCount()) return;
        if (!cells.equals(configuredCells) || !Arrays.equals(tabOrder, GlassConfig.tabOrder)
                || !Arrays.equals(tabHidden, GlassConfig.tabHidden)) {
            if (cells.equals(configuredCells)) {
                restoreTabLayout();
            }
            configuredCells = cells;
            nativeOrder = new View[row.getChildCount()];
            nativeVisibility = new int[nativeOrder.length];
            for (int i = 0; i < nativeOrder.length; i++) {
                nativeOrder[i] = row.getChildAt(i);
                nativeVisibility[i] = nativeOrder[i].getVisibility();
            }
            tabOrder = GlassConfig.tabOrder;
            tabHidden = GlassConfig.tabHidden;
            TabLayoutApplier.apply(row, "kugou");
            if (drag != null) drag.setTabRow(row);
            confirmed = -1;
            if (row.isLayoutRequested()) return;
        }
        if (visibleChildren(row) == 0) return;
        float density = root.getResources().getDisplayMetrics().density;
        tabContents.setEmpty();
        int first = row.getWidth();
        int last = 0;
        for (int i = 0; i < row.getChildCount(); i++) {
            View tab = row.getChildAt(i);
            if (!isVisibleTab(tab)) continue;
            first = Math.min(first, tab.getLeft());
            last = Math.max(last, tab.getRight());
            collectTabContents(tab, tab.getLeft(), tab.getTop());
        }
        // Only fall back to the tab centres when a skin draws its labels itself.
        int naturalHeight = Math.round(48 * density);
        int centerY = (row.getPaddingTop() + row.getHeight() - row.getPaddingBottom()) / 2;
        if (!tabContents.isEmpty()) {
            centerY = tabContents.centerY();
            naturalHeight = Math.max(naturalHeight, tabContents.height() + Math.round(12 * density));
        }
        int height = Math.round(naturalHeight * clamp(GlassConfig.pillScale, 0.1f, 3f));
        int width = Math.round((last - first - 16 * density)
                * clamp(GlassConfig.widthScale, 0.5f, 1.2f));
        if (width <= 0 || height <= 0) return;
        ViewGroup currentBackdrop = findBackdrop(section);
        if (boundBar != bar || backdrop != currentBackdrop) {
            removeGlass();
            backdrop = currentBackdrop;
            installGlass(bar, density);
        }
        rowBackground = clearBackground(row, rowBackground);
        barBackground = clearBackground(bar, barBackground);
        int center = row.getTop() + centerY
                + Math.round((12f - clamp(GlassConfig.barOffsetDp, 0f, 24f)) * density);
        bar.getLocationOnScreen(barLocation);
        bar.getWindowVisibleDisplayFrame(windowFrame);
        int tx = Math.round(row.getTranslationX());
        int ty = Math.round(row.getTranslationY());
        int gap = Math.round(6f * density);
        int minX = Math.max(0, windowFrame.left - barLocation[0]) + gap - tx;
        int maxX = Math.min(bar.getWidth(), windowFrame.right - barLocation[0]) - gap - tx;
        int minY = windowFrame.top - barLocation[1] + gap - ty;
        int maxY = windowFrame.bottom - barLocation[1] - gap - ty;
        if (maxX <= minX || maxY <= minY) {
            pillBounds.setEmpty();
            return;
        }
        width = Math.min(width, maxX - minX);
        height = Math.min(height, maxY - minY);
        // Prefer trimming the surface to shifting it away from the native labels.
        int centeredHeight = 2 * Math.min(center - minY, maxY - center);
        if (centeredHeight > 0) height = Math.min(height, centeredHeight);
        int left = row.getLeft() + (first + last - width) / 2
                + Math.round(clamp(GlassConfig.barOffsetXDp, -120f, 120f) * density);
        left = Math.max(minX, Math.min(left, maxX - width));
        int top = Math.max(minY, Math.min(center - height / 2, maxY - height));
        pillBounds.set(left, top, left + width, top + height);
        updatePlayer(bar, Math.round(GlassConfig.kugouPlayerLiftDp * density));
    }

    private void bind() {
        section = findShown(root, ADDITIONAL);
        if (section == null) return;
        View bar = findShown(section, BAR);
        if (!(bar instanceof FrameLayout)) return;
        FrameLayout nativeBar = (FrameLayout) bar;
        for (int i = 0; i < nativeBar.getChildCount(); i++) {
            View child = nativeBar.getChildAt(i);
            if (!(child instanceof LinearLayout) || !child.isShown()) continue;
            LinearLayout candidate = (LinearLayout) child;
            if (candidate.getOrientation() == LinearLayout.HORIZONTAL
                    && (ROW.equals(candidate.getClass().getName()) || visibleChildren(candidate) == 5)) {
                row = candidate;
                return;
            }
        }
    }

    private void updatePlayer(View bar, int lift) {
        if (lift <= 0 || player == null || !player.isShown()
                || !(player.getParent() instanceof LinearLayout)
                || !(player.getLayoutParams() instanceof LinearLayout.LayoutParams)) {
            restorePlayer();
            return;
        }
        LinearLayout stack = (LinearLayout) player.getParent();
        if (bar.getParent() != stack || stack.getOrientation() != LinearLayout.VERTICAL
                || stack.indexOfChild(player) >= stack.indexOfChild(bar)) {
            restorePlayer();
            return;
        }
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) player.getLayoutParams();
        if (lp != liftedParams || lp.bottomMargin != appliedMargin) nativeMargin = lp.bottomMargin;
        liftedParams = lp;
        appliedMargin = nativeMargin + lift;
        if (lp.bottomMargin != appliedMargin) {
            lp.bottomMargin = appliedMargin;
            player.setLayoutParams(lp);
        }
    }

    private void restorePlayer() {
        if (player != null && liftedParams != null && player.getLayoutParams() == liftedParams
                && liftedParams.bottomMargin == appliedMargin && appliedMargin != nativeMargin) {
            liftedParams.bottomMargin = nativeMargin;
            player.setLayoutParams(liftedParams);
        }
        liftedParams = null;
    }

    private void restoreTabLayout() {
        if (row == null || nativeOrder == null || row.getChildCount() != nativeOrder.length) return;
        for (View child : nativeOrder) if (child.getParent() != row) return;
        for (int i = 0; i < nativeOrder.length; i++) {
            View child = nativeOrder[i];
            if (row.getChildAt(i) != child) {
                row.removeView(child);
                row.addView(child, i);
            }
            child.setVisibility(nativeVisibility[i]);
        }
    }

    private void clearBinding() {
        removeGlass();
        restoreTabLayout();
        configuredCells = null;
        nativeOrder = null;
        nativeVisibility = null;
        restorePlayer();
        row = null;
        section = null;
        player = null;
        backdrop = null;
    }

    private void installGlass(FrameLayout bar, float density) {
        boundBar = bar;
        nativeClipChildren = bar.getClipChildren();
        nativeClipToPadding = bar.getClipToPadding();
        bar.setClipChildren(false);
        bar.setClipToPadding(false);
        host = new LiquidGlassHostLayout(bar.getContext(), backdrop, row);
        host.setupShadow(density, host.isDark());
        host.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        LiquidGlassPanel panel = new LiquidGlassPanel(bar.getContext(), backdrop, density, host.isDark());
        panel.useAppTuning();
        host.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        // Only draw the selected wash: native icon/text and touch stay above it.
        selection = new DropletPanel(bar.getContext(), backdrop, null, density, host.isDark());
        selection.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        selection.setVisibility(View.INVISIBLE);
        host.setGlassTuner(new LiquidGlassHostLayout.GlassTuner() {
            @Override public void onSize(int w, int h, float radius) { panel.invalidate(); }
            @Override public void onTheme(boolean dark) {
                panel.setTheme(dark);
                if (selection != null) selection.setTheme(dark);
            }
        });
        // Zero-sized layout params keep decorations out of native measurement.
        bar.addView(host, bar.indexOfChild(row), new FrameLayout.LayoutParams(0, 0));
        bar.addView(selection, bar.indexOfChild(row), new FrameLayout.LayoutParams(0, 0));
        pill = panel;
        selection.setTabRow(row);
        selection.setPill(panel);
        drag = new DropletDragController(selection, row, density, host.isDark());
        drag.setPositioner(this::placeSelection);
        BARS.put(bar, new WeakReference<>(this));
        host.attach(false);
        observer.addOnPreDrawListener(selectionObserver);
    }

    private void removeGlass() {
        if (boundBar == null) return;
        ViewTreeObserver tree = root.getViewTreeObserver();
        if (tree.isAlive()) tree.removeOnPreDrawListener(selectionObserver);
        if (row.getBackground() == null && rowBackground != null) row.setBackground(rowBackground);
        if (boundBar.getBackground() == null && barBackground != null) boundBar.setBackground(barBackground);
        boundBar.setClipChildren(nativeClipChildren);
        boundBar.setClipToPadding(nativeClipToPadding);
        BARS.remove(boundBar);
        ownsTouch = trackingTouch = false;
        confirmed = -1;
        confirmAfter = 0L;
        if (drag != null) {
            drag.setPositioner(null);
            drag.setTabRow(null);
        }
        drag = null;
        pill = null;
        boundBar.removeView(host);
        boundBar.removeView(selection);
        boundBar = null;
        host = null;
        selection = null;
        rowBackground = null;
        barBackground = null;
    }

    private void updateSelection() {
        if (selection == null || row == null || drag == null) return;
        boolean shown = row.isShown() && !pillBounds.isEmpty();
        host.setVisibility(shown ? View.VISIBLE : View.INVISIBLE);
        if (!shown) {
            selection.setVisibility(View.INVISIBLE);
            return;
        }
        host.setTranslationX(row.getTranslationX());
        host.setTranslationY(row.getTranslationY());
        host.setAlpha(row.getAlpha());
        selection.setAlpha(row.getAlpha());
        int pad = host.shadowPad();
        position(host, pillBounds.width() + 2 * pad, pillBounds.height() + 2 * pad,
                pillBounds.left - pad, pillBounds.top - pad);
        Object type = null;
        try {
            if (currentType != null) type = currentType.invoke(boundBar);
        } catch (ReflectiveOperationException ignored) { }
        int selected = -1;
        for (int i = 0, n = TabBarBridge.tabCount(row); i < n; i++) {
            View tab = TabBarBridge.tabAt(row, i);
            if (!isVisibleTab(tab)) continue;
            if (type != null && type.equals(tab.getTag())) {
                selected = i;
                break;
            }
            if (tab.isSelected() || tab.isActivated()) selected = i;
        }
        if (selected < 0) {
            confirmed = -1;
            selection.setVisibility(View.INVISIBLE);
            return;
        }
        selection.setVisibility(View.VISIBLE);
        selection.setConfirmedIndex(selected);
        if (selected != confirmed || android.os.SystemClock.uptimeMillis() >= confirmAfter) {
            drag.syncConfirmedIndex(selected, confirmed < 0);
        }
        confirmed = selected;
        drag.refreshGeometry();
    }

    private void placeSelection(float index, float sx, float sy, float progress) {
        if (selection == null || confirmed < 0 || pillBounds.isEmpty()) return;
        int count = TabBarBridge.tabCount(row);
        if (count == 0) return;
        float slot = clamp(index, 0, count - 1);
        int from = (int) slot;
        View a = TabBarBridge.tabAt(row, from);
        View b = TabBarBridge.tabAt(row, Math.min(from + 1, count - 1));
        float f = slot - from;
        float center = row.getLeft() + a.getX() + a.getWidth() * 0.5f
                + f * (b.getX() + b.getWidth() * 0.5f - a.getX() - a.getWidth() * 0.5f);
        int width = Math.min(pillBounds.width(), Math.round(a.getWidth() + f * (b.getWidth() - a.getWidth())));
        int inset = Math.round(4 * row.getResources().getDisplayMetrics().density);
        int height = Math.min(a.getHeight(), pillBounds.height()) - 2 * inset;
        if (width <= 0 || height <= 0) {
            selection.setVisibility(View.INVISIBLE);
            return;
        }
        float tx = row.getTranslationX(), ty = row.getTranslationY();
        // At rest keep the entire selection inside the pill. When pressed, allow
        // the same expansion as QQ/WeChat, bounded by the visible window.
        int centeredWidth = (int) (2f * Math.min(center - pillBounds.left, pillBounds.right - center));
        if (centeredWidth >= height) width = Math.min(width, centeredWidth);
        center = clamp(center, pillBounds.left + width / 2f, pillBounds.right - width / 2f);
        float minX = windowFrame.left - barLocation[0] - tx;
        float maxX = windowFrame.right - barLocation[0] - tx;
        float minY = windowFrame.top - barLocation[1] - ty;
        float maxY = windowFrame.bottom - barLocation[1] - ty;
        sx = Math.min(sx, (maxX - minX) / width);
        sy = Math.min(sy, (maxY - minY) / height);
        center = clamp(center, minX + width * sx / 2f, maxX - width * sx / 2f);
        float cy = clamp(pillBounds.exactCenterY(), minY + height * sy / 2f, maxY - height * sy / 2f);
        position(selection, width, height, 0, 0);
        selection.setTranslationX(center + tx - width / 2f);
        selection.setTranslationY(cy + ty - height / 2f);
        selection.setScaleX(sx);
        selection.setScaleY(sy);
        pill.setInteraction(progress, center - pillBounds.left);
    }

    private void collectTabContents(View view, int x, int y) {
        if (view.getVisibility() != View.VISIBLE || view.getWidth() <= 0 || view.getHeight() <= 0) return;
        if (view instanceof ImageView || view instanceof TextView) {
            int left = x + view.getPaddingLeft();
            int top = y + view.getPaddingTop();
            int right = x + view.getWidth() - view.getPaddingRight();
            int bottom = y + view.getHeight() - view.getPaddingBottom();
            if (right > left && bottom > top) tabContents.union(left, top, right, bottom);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                collectTabContents(child, x + child.getLeft(), y + child.getTop());
            }
        }
    }

    private static Drawable clearBackground(View view, Drawable previous) {
        Drawable current = view.getBackground();
        if (current == null) return previous;
        int l = view.getPaddingLeft(), t = view.getPaddingTop();
        int r = view.getPaddingRight(), b = view.getPaddingBottom();
        view.setBackground(null);
        view.setPadding(l, t, r, b);
        return current;
    }

    private static float clamp(float value, float min, float max) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : 1f;
    }

    private static void position(View view, int width, int height, int left, int top) {
        if (view.isLayoutRequested() || view.getMeasuredWidth() != width || view.getMeasuredHeight() != height) {
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        }
        if (view.isLayoutRequested() || view.getLeft() != left || view.getTop() != top
                || view.getWidth() != width || view.getHeight() != height) {
            view.layout(left, top, left + width, top + height);
        }
    }

    private ViewGroup findBackdrop(View section) {
        if (!row.getGlobalVisibleRect(rowBounds)) return null;
        View branch = section;
        while (branch != root && branch.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) branch.getParent();
            for (int i = parent.indexOfChild(branch) - 1; i >= 0; i--) {
                View candidate = parent.getChildAt(i);
                if (candidate instanceof ViewGroup && candidate.isShown()
                        && candidate.getGlobalVisibleRect(contentBounds)
                        && Rect.intersects(rowBounds, contentBounds)) return (ViewGroup) candidate;
            }
            branch = parent;
        }
        return null; // Static surface when the native layout reserves its own bottom area.
    }

    private static int visibleChildren(ViewGroup group) {
        int count = 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (isVisibleTab(child)) count++;
        }
        return count;
    }

    private static boolean isVisibleTab(View tab) {
        return tab.getVisibility() == View.VISIBLE && tab.getWidth() > 0 && isNativeTab(tab);
    }

    private static boolean isNativeTab(View tab) {
        for (Class<?> type = tab.getClass(); type != null; type = type.getSuperclass()) {
            if (TAB_ITEM.equals(type.getName())) return true;
        }
        return false;
    }

    private static View findShown(View view, String name) {
        if (view.getVisibility() != View.VISIBLE) return null;
        if (name.equals(view.getClass().getName())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View result = findShown(group.getChildAt(i), name);
                if (result != null) return result;
            }
        }
        return null;
    }
}
