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
        List<WeakReference<View>> expected = APPLIED_ROWS.get(row);
        boolean sameOrder = expected == null || expected.size() == row.getChildCount();
        if (expected != null && sameOrder) {
            for (int i = 0; i < expected.size(); i++) {
                if (expected.get(i).get() != row.getChildAt(i)) { sameOrder = false; break; }
            }
        }
        if (sameOrder && known != null && known.size() == row.getChildCount()) {
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
    static String label(View cell) {
        if (cell instanceof PinduoduoHomeBar.TabCopy copy) return clean(copy.title.getText());
        String text = text(cell, 0);
        return !text.isEmpty() ? text : clean(cell.getContentDescription());
    }
    private static String text(View view, int depth) {
        if (depth > 8) return "";
        if (view instanceof TextView tv) {
            String value = clean(tv.getText());
            // Unread counters are not navigation titles.
            if (!value.matches("[0-9０-９+＋.· ]+") && !value.isEmpty()) return value;
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                String value = text(group.getChildAt(i), depth + 1);
                if (!value.isEmpty()) return value;
            }
        }
        return "";
    }
    private static String clean(CharSequence text) {
        if (text == null) return "";
        String value = text.toString().trim();
        return TabCatalogRules.validText(value) ? value : "";
    }
    static String layoutKey(String appKey, View cell) {
        if (cell instanceof PinduoduoHomeBar.TabCopy copy) return copy.layoutTitle();
        return TabCatalogRules.normalize(appKey, label(cell));
    }
    static void observeRow(ViewGroup row, String appKey) {
        if (row == null || TabCatalogRules.packageFor(appKey) == null) return;
        List<View> tabs = new ArrayList<>();
        for (View cell : nativeCells(row)) {
            if (nativeVisibility(cell) != View.GONE) tabs.add(cell);
        }
        reportCells(row.getContext(), appKey, tabs);
    }
    static void reportCells(Context context, String appKey, List<? extends View> cells) {
        String[] keys = new String[cells.size()], labels = new String[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            labels[i] = label(cells.get(i));
            keys[i] = layoutKey(appKey, cells.get(i));
        }
        publish(context, appKey, keys, labels);
    }
    private static String rowKeys(ViewGroup row, String appKey) {
        ArrayList<String> keys = new ArrayList<>();
        for (View cell : nativeCells(row)) {
            if (nativeVisibility(cell) != View.GONE) keys.add(layoutKey(appKey, cell));
        }
        return new org.json.JSONArray(keys).toString();
    }
    static void watch(ViewGroup row, String appKey) {
        observeRow(row, appKey);
        if (row == null || WATCHED.put(row, true) != null) return;
        OBSERVED_KEYS.put(row, rowKeys(row, appKey));
        WeakReference<ViewGroup> ref = new WeakReference<>(row);
        row.postDelayed(new Runnable() {
            @Override public void run() {
                ViewGroup current = ref.get();
                if (current == null) return;
                if (!current.isAttachedToWindow()) { WATCHED.remove(current); return; }
                nativeCells(current);
                String keys = rowKeys(current, appKey);
                if (!keys.equals(OBSERVED_KEYS.put(current, keys))) {
                    if ("taobao".equals(appKey)) NativeTabLayout.applyTaobao(current);
                    else if ("alipay".equals(appKey)) NativeTabLayout.applyAlipay(current);
                    else TabLayoutApplier.apply(current, appKey);
                }
                observeRow(current, appKey);
                current.postDelayed(this, 1500);
            }
        }, 1500);
    }
    static void publish(Context context, String appKey, String[] keys, String[] labels) {
        if (TabCatalogRules.packageFor(appKey) == null || keys.length < 2
                || keys.length > TabCatalogRules.MAX_TABS || keys.length != labels.length) return;
        HashSet<String> seen = new HashSet<>();
        for (int i = 0; i < keys.length; i++) {
            if (!TabCatalogRules.validText(keys[i]) || !TabCatalogRules.validText(labels[i])
                    || !seen.add(keys[i])) return; // Never overwrite a good list with partial/ambiguous data.
        }
        String fingerprint = new org.json.JSONArray(Arrays.asList(keys)).toString()
                + new org.json.JSONArray(Arrays.asList(labels)).toString();
        if (fingerprint.equals(SENT.get(appKey))) return;
        long now = SystemClock.uptimeMillis();
        Long attempt = ATTEMPT.get(appKey);
        if (attempt != null && now - attempt < 3000) return;
        ATTEMPT.put(appKey, now);
        Context application = context.getApplicationContext();
        Context app = application == null ? context : application;
        String[] ids = keys.clone(), titles = labels.clone();
        IO.execute(() -> {
            try {
                String pkg = TabCatalogRules.packageFor(appKey);
                PackageInfo info = app.getPackageManager().getPackageInfo(pkg, 0);
                Bundle extras = new Bundle();
                extras.putStringArray("keys", ids);
                extras.putStringArray("labels", titles);
                extras.putLong("version_code", info.getLongVersionCode());
                extras.putLong("updated_at", info.lastUpdateTime);
                Bundle result = app.getContentResolver().call(TabCatalogProvider.URI,
                        TabCatalogProvider.REPORT, appKey, extras);
                if (result != null && result.getBoolean("accepted")) {
                    SENT.put(appKey, fingerprint);
                    LiquidGlassModule.log(android.util.Log.INFO,
                            "native tabs synced: app=" + appKey + " titles=" + Arrays.toString(titles));
                }
            } catch (Exception failure) {
                // Retry on the next observation. No Binder or disk IO on the drawing thread.
                LiquidGlassModule.log(android.util.Log.WARN,
                        "native tabs sync unavailable: app=" + appKey + " " + failure.getClass().getSimpleName());
            }
        });
    }
}