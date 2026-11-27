package io.github.liuran001.mmliquidglass;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reads only an already identified native bar, before layout hiding/reordering. */
final class RuntimeTabCatalog {
    private static final Map<ViewGroup, List<WeakReference<View>>> NATIVE_ROWS = new WeakHashMap<>();
    private static final Map<ViewGroup, List<WeakReference<View>>> APPLIED_ROWS = new WeakHashMap<>();
    private static final Map<View, Integer> NATIVE_VISIBILITY = new WeakHashMap<>();
    private static final Map<View, Integer> APPLIED_VISIBILITY = new WeakHashMap<>();
    private static final Map<ViewGroup, String> OBSERVED_KEYS = new WeakHashMap<>();
    private static final Map<ViewGroup, Boolean> WATCHED = new WeakHashMap<>();
    private static final ConcurrentHashMap<String, String> SENT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> ATTEMPT = new ConcurrentHashMap<>();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "glass-tab-catalog");
        t.setDaemon(true);
        return t;
    });
    private RuntimeTabCatalog() {}

    /** Keep the pre-rewrite view order; hidden cells are still part of the catalog. */
    static List<View> nativeCells(ViewGroup row) {
        List<WeakReference<View>> known = NATIVE_ROWS.get(row);
        List<View> cells = new ArrayList<>();
        if (known != null && known.size() == row.getChildCount()) {
            for (WeakReference<View> ref : known) {
                View view = ref.get();
                if (view == null || view.getParent() != row) { cells.clear(); break; }
                cells.add(view);
            }
        }
        if (cells.size() != row.getChildCount()) {
            cells.clear();
            List<WeakReference<View>> refs = new ArrayList<>();
            for (int i = 0; i < row.getChildCount(); i++) {
                View view = row.getChildAt(i);
                cells.add(view);
                if (!NATIVE_VISIBILITY.containsKey(view)) NATIVE_VISIBILITY.put(view, view.getVisibility());
                refs.add(new WeakReference<>(view));
            }
            NATIVE_ROWS.put(row, refs);
            APPLIED_ROWS.put(row, new ArrayList<>(refs));
        }
        return cells;
    }
    static int nativeVisibility(View view) {
        Integer expected = APPLIED_VISIBILITY.get(view);
        int actual = view.getVisibility();
        if (expected == null || actual != expected) NATIVE_VISIBILITY.put(view, actual);
        return NATIVE_VISIBILITY.getOrDefault(view, actual);
    }
    static void restoreRow(ViewGroup row) {
        List<View> cells = nativeCells(row);
        for (int i = 0; i < cells.size(); i++) {
            View cell = cells.get(i);
            if (row.getChildAt(i) != cell) {
                row.removeView(cell);
                row.addView(cell, i);
            }
            int visibility = nativeVisibility(cell);
            if (cell.getVisibility() != visibility) cell.setVisibility(visibility);
        }
        markApplied(row);
    }
    static void markApplied(ViewGroup row) {
        List<WeakReference<View>> refs = new ArrayList<>();
        for (int i = 0; i < row.getChildCount(); i++) {
            View cell = row.getChildAt(i);
            refs.add(new WeakReference<>(cell));
            APPLIED_VISIBILITY.put(cell, cell.getVisibility());
        }
        APPLIED_ROWS.put(row, refs);
    }
    static final class Catalog {
        final String appKey;
        final List<String> keys = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        final List<List<String>> candidates = new ArrayList<>();
        final List<Integer> indices = new ArrayList<>();
        final List<Boolean> locked = new ArrayList<>();
        Catalog(String appKey) { this.appKey = appKey; }
    }
    private static final Map<View, String> CELL_KEYS = new WeakHashMap<>();
    private static final Map<String, Catalog> CATALOGS = new java.util.HashMap<>();

    private static View source(View cell) {
        if (cell instanceof RailwayHomeBar.TabCopy copy) return copy.source;
        return cell instanceof PinduoduoHomeBar.TabCopy copy ? copy.source : cell;
    }
    private static final class TextReading {
        final TextView view;
        final TabCatalogRules.LabelCandidate candidate;
        TextReading(TextView view, TabCatalogRules.LabelCandidate candidate) {
            this.view = view; this.candidate = candidate;
        }
    }
    private static String clean(CharSequence text) {
        if (text == null) return "";
        String value = text.toString().replace('\n', ' ').replace('\r', ' ').trim();
        return TabCatalogRules.validText(value) ? value : "";
    }
    private static String resourceName(View view) {
        if (view.getId() == View.NO_ID) return "";
        try { return view.getResources().getResourceEntryName(view.getId()); }
        catch (RuntimeException ignored) { return Integer.toHexString(view.getId()); }
    }
    private static void readings(View view, View cell, float x, float y, int depth,
            boolean badgeParent, List<TextReading> out) {
        if (view == null || depth > 10 || out.size() >= TabCatalogRules.MAX_CANDIDATES
                || (view != cell && view.getVisibility() != View.VISIBLE)) return;
        String name = resourceName(view).toLowerCase(java.util.Locale.ROOT);
        boolean badge = badgeParent || name.matches(".*(?:badge|unread|red_dot|reddot|bubble|promotion).*");
        float density = cell.getResources().getDisplayMetrics().density;
        String text = view instanceof TextView tv ? clean(tv.getText()) : "";
        if (!text.isEmpty() && view instanceof TextView tv) {
            double vertical = cell.getHeight() <= 0 ? 0.5 : (y + tv.getHeight() * 0.5) / cell.getHeight();
            double center = cell.getWidth() <= 0 ? 0.5 : 1 - Math.min(1,
                    Math.abs(x + tv.getWidth() * 0.5 - cell.getWidth() * 0.5) / (cell.getWidth() * 0.5));
            out.add(new TextReading(tv, new TabCatalogRules.LabelCandidate(text,
                    (cell.getTop() + y + tv.getBaseline()) / density, vertical, center,
                    tv.getTextSize() / cell.getResources().getDisplayMetrics().scaledDensity,
                    view == cell, false, badge)));
        }
        String description = clean(view.getContentDescription());
        if (!description.isEmpty()) {
            String title = clean(TabCatalogRules.accessibilityTitle(description));
            if (!title.isEmpty()) out.add(new TextReading(null, new TabCatalogRules.LabelCandidate(
                    title, Double.NaN, 0.5, 0.5, 0, false, true, badge)));
            if (!title.equals(description) && out.size() < TabCatalogRules.MAX_CANDIDATES)
                out.add(new TextReading(null, new TabCatalogRules.LabelCandidate(
                        description, Double.NaN, 0.5, 0.5, 0, false, true, true)));
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                readings(child, cell, x + child.getLeft() + child.getTranslationX() - group.getScrollX(),
                        y + child.getTop() + child.getTranslationY() - group.getScrollY(),
                        depth + 1, badge, out);
            }
        }
    }
    private static List<TextReading> readings(View cell) {
        List<TextReading> out = new ArrayList<>();
        readings(source(cell), source(cell), 0, 0, 0, false, out);
        return out;
    }
    /** For bar discovery only. Catalog names are chosen using the complete row. */
    static String label(View cell) {
        if (cell == null) return "";
        List<TabCatalogRules.LabelCandidate> found = new ArrayList<>();
        for (TextReading reading : readings(cell)) found.add(reading.candidate);
        if (found.isEmpty()) return "";
        return TabCatalogRules.chooseLabels(GlassConfig.key(), List.of(found)).get(0);
    }
    static TextView titleView(View cell) {
        List<TextReading> found = readings(cell);
        List<TabCatalogRules.LabelCandidate> text = new ArrayList<>();
        for (TextReading reading : found) if (reading.view != null) text.add(reading.candidate);
        if (text.isEmpty()) return null;
        String selected = TabCatalogRules.chooseLabels(GlassConfig.key(), List.of(text)).get(0);
        for (TextReading reading : found) {
            if (reading.view != null && selected.equals(reading.candidate.text())) return reading.view;
        }
        return null;
    }
    static boolean isBiliPublishAction(View cell) {
        for (TextReading reading : readings(cell)) {
            if (TabCatalogRules.isBiliPublishActionLabel(reading.candidate.text())) return true;
        }
        String resource = resourceName(source(cell)).toLowerCase(java.util.Locale.ROOT);
        return resource.contains("center_plus") || resource.contains("tab_publish");
    }
    static String layoutKey(String appKey, View cell) {
        return CELL_KEYS.getOrDefault(cell, CELL_KEYS.getOrDefault(source(cell), ""));
    }
    static boolean isLockedKey(String appKey, String key) {
        Catalog catalog = CATALOGS.get(appKey);
        int index = catalog == null ? -1 : catalog.keys.indexOf(key);
        return index >= 0 ? catalog.locked.get(index) : TabCatalogRules.locked(appKey, key);
    }
    static Catalog reportCells(Context context, String appKey, List<? extends View> cells) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) indices.add(i);
        return reportCells(context, appKey, cells, indices);
    }
    static Catalog reportCells(Context context, String appKey, List<? extends View> cells, List<Integer> indices) {
        Catalog catalog = new Catalog(appKey);
        if (cells.size() < 2 || cells.size() > TabCatalogRules.MAX_TABS || cells.size() != indices.size()) return catalog;
        List<List<TabCatalogRules.LabelCandidate>> all = new ArrayList<>();
        for (View cell : cells) {
            List<TabCatalogRules.LabelCandidate> row = new ArrayList<>();
            for (TextReading reading : readings(cell)) row.add(reading.candidate);
            all.add(row);
        }
        List<String> automatic = TabCatalogRules.chooseLabels(appKey, all);
        int account = TabCatalogRules.accountIndex(appKey, automatic);
        for (int i = 0; i < cells.size(); i++) {
            View cell = source(cells.get(i));
            List<String> choices = new ArrayList<>();
            // The same native slot may have different text when selected or promoted.
            boolean locked = i == 0 || i == account;
            for (TabCatalogRules.LabelCandidate candidate : all.get(i)) {
                if (!choices.contains(candidate.text()) && choices.size() < TabCatalogRules.MAX_CANDIDATES)
                    choices.add(candidate.text());
            }
            String label = automatic.get(i);
            if (!choices.contains(label)) {
                if (choices.size() == TabCatalogRules.MAX_CANDIDATES) choices.remove(choices.size() - 1);
                choices.add(0, label);
            }
            String key = TabCatalogRules.cellKey(indices.get(i),
                    cell.getClass().getName() + ":" + resourceName(cell));
            catalog.keys.add(key); catalog.labels.add(label); catalog.candidates.add(choices);
            catalog.indices.add(indices.get(i)); catalog.locked.add(locked);
            CELL_KEYS.put(cells.get(i), key); CELL_KEYS.put(cell, key);
        }
        Catalog previous = CATALOGS.get(appKey);
        if (previous != null && previous.keys.equals(catalog.keys)) {
            publish(context, previous);
            return previous;
        }
        CATALOGS.put(appKey, catalog);

        publish(context, catalog);
        return catalog;
    }
    static void observeRow(ViewGroup row, String appKey) {
        if (row == null || TabCatalogRules.packageFor(appKey) == null) return;
        if ("bili".equals(appKey)) {
            List<View> biliCells = nativeCells(row);
            List<View> nav = new ArrayList<>(); List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < biliCells.size(); i++) {
                View cell = biliCells.get(i);
                if (isBiliPublishAction(cell)) continue;
                if (nativeVisibility(cell) != View.GONE) { nav.add(cell); indices.add(i); }
            }
            reportCells(row.getContext(), appKey, nav, indices);
            return;
        }
        List<View> tabs = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        List<View> nativeTabs = nativeCells(row);
        for (int i = 0; i < nativeTabs.size(); i++) {
            View cell = nativeTabs.get(i);
            if (nativeVisibility(cell) != View.GONE) { tabs.add(cell); indices.add(i); }
        }
        reportCells(row.getContext(), appKey, tabs, indices);
    }
    private static String rowKeys(ViewGroup row, String appKey) {
        ArrayList<String> keys = new ArrayList<>();
        List<View> nativeTabs = nativeCells(row);
        for (View cell : nativeTabs) {
            if ("bili".equals(appKey) && isBiliPublishAction(cell)) continue;
            if (nativeVisibility(cell) != View.GONE) keys.add(layoutKey(appKey, cell));
        }
        return new org.json.JSONArray(keys).toString();
    }
    static void watch(ViewGroup row, String appKey) {
        observeRow(row, appKey);
        if (row == null || WATCHED.put(row, true) != null) return;
        OBSERVED_KEYS.put(row, rowKeys(row, appKey));
        guardAppliedLayout(row, appKey);

        WeakReference<ViewGroup> ref = new WeakReference<>(row);
        row.postDelayed(new Runnable() {
            @Override public void run() {
                ViewGroup current = ref.get();
                if (current == null) return;
                if (!current.isAttachedToWindow()) { WATCHED.remove(current); return; }
                nativeCells(current);
                observeRow(current, appKey);
                String keys = rowKeys(current, appKey);
                if (!keys.equals(OBSERVED_KEYS.put(current, keys))) {
                    if ("taobao".equals(appKey)) NativeTabLayout.applyTaobao(current);
                    else if ("alipay".equals(appKey)) NativeTabLayout.applyAlipay(current);
                    else TabLayoutApplier.apply(current, appKey);
                }
                current.postDelayed(this, 1500);
            }
        }, 1500);
    }
    private static void guardAppliedLayout(ViewGroup row, String appKey) {
        android.view.ViewTreeObserver.OnPreDrawListener guard = () -> {
            List<WeakReference<View>> applied = APPLIED_ROWS.get(row);
            if (applied == null || applied.size() != row.getChildCount()) return true;
            boolean changed = false;
            for (int i = 0; i < applied.size(); i++) {
                View cell = applied.get(i).get();
                if (cell == null || cell.getParent() != row) return true;
                Integer visibility = APPLIED_VISIBILITY.get(cell);
                changed |= cell != row.getChildAt(i)
                        || (visibility != null && visibility == View.GONE && cell.getVisibility() != View.GONE);
            }
            if (!changed || GlassConfig.tabOrder.length + GlassConfig.tabHidden.length == 0) return true;
            if ("taobao".equals(appKey)) NativeTabLayout.applyTaobao(row);
            else if ("alipay".equals(appKey)) NativeTabLayout.applyAlipay(row);
            else TabLayoutApplier.apply(row, appKey);
            return !row.isLayoutRequested();
        };
        row.getViewTreeObserver().addOnPreDrawListener(guard);
        row.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {}
            @Override public void onViewDetachedFromWindow(View view) {
                row.getViewTreeObserver().removeOnPreDrawListener(guard);
                row.removeOnAttachStateChangeListener(this);
                WATCHED.remove(row);
            }
        });
    }
    private static void publish(Context context, Catalog catalog) {

        String appKey = catalog.appKey;
        if (TabCatalogRules.packageFor(appKey) == null || catalog.keys.size() < 2) return;
        org.json.JSONArray items = new org.json.JSONArray();
        try {
            for (int i = 0; i < catalog.keys.size(); i++) {
                items.put(new org.json.JSONObject().put("key", catalog.keys.get(i))
                        .put("label", catalog.labels.get(i)).put("native_index", catalog.indices.get(i))
                        .put("candidates", new org.json.JSONArray(catalog.candidates.get(i))));
            }
        } catch (org.json.JSONException invalid) { return; }
        String fingerprint = items.toString();
        if (fingerprint.equals(SENT.get(appKey))) return;
        long now = SystemClock.uptimeMillis();
        Long attempt = ATTEMPT.get(appKey);
        if (attempt != null && now - attempt < 3000) return;
        ATTEMPT.put(appKey, now);
        Context application = context.getApplicationContext();
        Context app = application == null ? context : application;
        IO.execute(() -> {
            try {
                String pkg = TabCatalogRules.packageFor(appKey);
                PackageInfo info = app.getPackageManager().getPackageInfo(pkg, 0);
                Bundle extras = new Bundle();
                extras.putInt("schema", TabCatalogRules.SCHEMA);
                extras.putString("items", fingerprint);
                extras.putLong("version_code", info.getLongVersionCode());
                extras.putLong("updated_at", info.lastUpdateTime);
                Bundle result = app.getContentResolver().call(TabCatalogProvider.URI,
                        TabCatalogProvider.REPORT, appKey, extras);
                if (result != null && result.getBoolean("accepted")) {
                    SENT.put(appKey, fingerprint);
                    LiquidGlassModule.log(android.util.Log.INFO,
                            "native tabs synced: app=" + appKey + " titles=" + catalog.labels);
                }
            } catch (Exception failure) {
                LiquidGlassModule.log(android.util.Log.WARN,
                        "native tabs sync unavailable: app=" + appKey + " " + failure.getClass().getSimpleName());
            }
        });
    }
}
