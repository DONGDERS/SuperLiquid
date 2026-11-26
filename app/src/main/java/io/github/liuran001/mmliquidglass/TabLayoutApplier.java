package io.github.liuran001.mmliquidglass;

import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies the manager's floating-bar tab layout config to a REAL host bar
 * (WeChat / QQ — the app's own tab row, glassified in place).
 *
 * <p>Mechanism: reorder the row's children to the configured order and GONE
 * the hidden ones. Each cell keeps its own click listener (bound to the cell,
 * not its position), so clicks still open the right page. The app-side switch
 * methods address children by position, so after a reorder the selection
 * highlight is corrected post-hoc by {@link #correctSelection} — the
 * <app>TabBarBridge switch hook runs it after every native setTo.
 *
 * <p>Basics can never be hidden: the default-entry tab (position 0) plus the
 * "my/mine" family. Layouts address native cell identities, independently of the displayed title.
 */
final class TabLayoutApplier {

    /** Row position -> logical cell index, for the bar currently rewritten. */
    private static volatile int[] sPos2Logical;
    private static volatile ViewGroup sRow;

    private TabLayoutApplier() {
    }

    /** Same locked titles as the runtime catalog shown by the manager. */
    static boolean isLockedTitle(String appKey, String title) {
        return RuntimeTabCatalog.isLockedKey(appKey, title);
    }
    private static String normalizeTitle(String appKey, String title) {
        return TabCatalogRules.normalize(appKey, title);
    }
    /**
     * Rewrite the row per GlassConfig.tabOrder / tabHidden. Idempotent:
     * safe to call again after a structural refresh (the mapping is rebuilt
     * from the CURRENT child order each time).
     */
    static void apply(ViewGroup row, String appKey) {
        if (row == null) {
            clear();
            return;
        }
        try {
            int n = row.getChildCount();
            if (n < 2 || n > TabCatalogRules.MAX_TABS) {
                RuntimeTabCatalog.restoreRow(row);
                LiquidGlassModule.log(android.util.Log.WARN,
                        "tab layout skipped: app=" + appKey + " childCount=" + n);
                clear();
                return;
            }
            List<View> cells = RuntimeTabCatalog.nativeCells(row);
            RuntimeTabCatalog.observeRow(row, appKey);
            String[] titles = new String[n];
            List<String> currentKeys = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                View cell = cells.get(i);
                titles[i] = RuntimeTabCatalog.layoutKey(appKey, cell);
                if (RuntimeTabCatalog.nativeVisibility(cell) == View.GONE) continue;
                if (titles[i].isEmpty() || currentKeys.contains(titles[i])) {
                    RuntimeTabCatalog.restoreRow(row);
                    clear();
                    return; // No positional guess after a host update.
                }
                currentKeys.add(titles[i]);
            }
            if (!GlassConfig.matchesTabCatalog(currentKeys)
                    || (GlassConfig.tabOrder.length == 0 && GlassConfig.tabHidden.length == 0)) {
                RuntimeTabCatalog.restoreRow(row);
                LiquidGlassModule.log(android.util.Log.INFO,
                        "tab layout: no config for app=" + appKey
                                + ", native order " + java.util.Arrays.toString(titles));
                clear();
                return;
            }
            // Hidden set; basics locked. Entry tab always survives a title
            // mismatch too.
            Set<String> hidden = new HashSet<>();
            for (String h : GlassConfig.tabHidden) {
                h = normalizeTitle(appKey, h);
                if (h != null && !isLockedTitle(appKey, h)) hidden.add(h);
            }
            int entry = 0;
            while (entry < n && RuntimeTabCatalog.nativeVisibility(cells.get(entry)) == View.GONE) entry++;
            if (entry == n) { clear(); return; }
            hidden.remove(titles[entry]);
            // Display order: configured titles first, then the rest natively.
            List<Integer> disp = new ArrayList<>();
            boolean[] used = new boolean[n];
            for (String want : GlassConfig.tabOrder) {
                if (want == null) continue;
                want = normalizeTitle(appKey, want);
                for (int i = 0; i < n; i++) {
                    if (RuntimeTabCatalog.nativeVisibility(cells.get(i)) != View.GONE
                            && !used[i] && want.equals(titles[i]) && !hidden.contains(titles[i])) {
                        used[i] = true;
                        disp.add(i);
                        break;
                    }
                }
            }
            for (int i = 0; i < n; i++) {
                if (RuntimeTabCatalog.nativeVisibility(cells.get(i)) != View.GONE
                        && !used[i] && !hidden.contains(titles[i])) disp.add(i);
            }
            // Hard rule: the entry tab (logical 0) is PINNED first — it can
            // never be pushed below slot 0, whatever the stored order says.
            disp.remove(Integer.valueOf(entry));
            disp.add(0, entry);
            // Titles first — if anything below throws, the evidence line
            // already told us what the live bar has.
            LiquidGlassModule.log(android.util.Log.INFO, "tab layout: app=" + appKey
                    + " titles=" + java.util.Arrays.toString(titles)
                    + " order=" + java.util.Arrays.toString(GlassConfig.tabOrder)
                    + " hidden=" + hidden);
            int pos = 0;
            for (int logical : disp) {
                View cell = cells.get(logical);
                // addView() throws "child already has a parent" even for a
                // same-parent move — detach first, then insert.
                row.removeView(cell);
                row.addView(cell, Math.min(pos, row.getChildCount()));
                cell.setVisibility(View.VISIBLE);
                pos++;
            }
            for (int i = 0; i < n; i++) {
                if (hidden.contains(titles[i])) cells.get(i).setVisibility(View.GONE);
            }
            int[] m = new int[disp.size()];
            for (int p = 0; p < disp.size(); p++) m[p] = disp.get(p);
            sPos2Logical = m;
            sRow = row;
            RuntimeTabCatalog.markApplied(row);
            // Evidence line: config + what titles the live bar actually has —
            // a title mismatch (server rebound labels) shows up right here.
            LiquidGlassModule.log(android.util.Log.INFO, "tab layout applied: app=" + appKey
                    + " visible=" + disp.size() + "/" + n
                    + " titles=" + java.util.Arrays.toString(titles)
                    + " order=" + java.util.Arrays.toString(GlassConfig.tabOrder)
                    + " hidden=" + hidden);
        } catch (Throwable t) {
            LiquidGlassModule.logErr("tab layout apply failed", t);
            clear();
        }
    }

    /** Whether {@code ancestor} is {@code v} or one of its view ancestors. */
    private static boolean isAncestorOf(View ancestor, View v) {
        if (ancestor == null || v == null) {
            return false;
        }
        android.view.ViewParent p = v.getParent();
        while (p instanceof View) {
            if (p == ancestor) {
                return true;
            }
            p = p.getParent();
        }
        return false;
    }
    static boolean hasMapping(ViewGroup row) {
        return row != null && row == sRow && sPos2Logical != null;
    }

    static int slotForLogicalIndex(ViewGroup row, int logicalIndex) {
        int[] map = sPos2Logical;
        if (!hasMapping(row) || map == null) return -1;
        int slot = 0;
        for (int p = 0; p < row.getChildCount(); p++) {
            if (row.getChildAt(p).getVisibility() == View.GONE) continue;
            if (slot < map.length && map[slot] == logicalIndex) return slot;
            slot++;
        }
        return -1;
    }

    static void clear() {
        sPos2Logical = null;
        sRow = null;
    }

    /**
     * Post-switch selection fix-up: the app addressed a cell positionally
     * with the LOGICAL tab index; re-assert each cell's selected state from
     * our mapping so the highlight lands on the right cell.
     */
    static void correctSelection(View bar, int logicalIndex) {
        int[] m = sPos2Logical;
        ViewGroup row = sRow;
        if (m == null || row == null) {
            return; // no rewrite in effect: native selection is already right
        }
        // The hook hands us the bar itself (WeChat's LauncherUIBottomTabView,
        // QQ's QQTabLayout), while apply() recorded the row *inside* it. Requiring
        // identity here meant this never ran for either of those: only QQ's
        // QQTabWidget, which doubles as its own row, matched. Accept any bar the
        // rewritten row belongs to instead.
        if (bar != row && !isAncestorOf(bar, row)) {
            return;
        }
        try {
            boolean fixed = false;
            int slot = 0;
            for (int p = 0; p < row.getChildCount(); p++) {
                View cell = row.getChildAt(p);
                boolean visible = cell.getVisibility() != View.GONE;
                boolean want = visible && slot < m.length && m[slot] == logicalIndex;
                if (visible) slot++;
                if (cell.isSelected() != want) {
                    cell.setSelected(want);
                    fixed = true;
                }
            }
            // First actual fix-up proves the translation path is alive.
            if (fixed && !sFixLogged) {
                sFixLogged = true;
                LiquidGlassModule.log(android.util.Log.INFO,
                        "tab selection fix active: logical=" + logicalIndex);
            }
        } catch (Throwable t) {
            LiquidGlassModule.logErr("tab selection fix failed", t);
        }
    }

    private static volatile boolean sFixLogged;
}
