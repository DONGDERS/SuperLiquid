package io.github.liuran001.mmliquidglass;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
 * "my/mine" family. Titles are matched against each cell's first TextView.
 */
final class TabLayoutApplier {

    /** Row position -> logical cell index, for the bar currently rewritten. */
    private static volatile int[] sPos2Logical;
    private static volatile ViewGroup sRow;

    private TabLayoutApplier() {
    }

    /** Basics per app key (mirrors TabLayoutEditor.LOCKED in the manager):
     *  verified by TA on the live bars: WeChat first tab IS 微信;
     *  QQ order is 消息/频道/联系人/动态 and locks 消息+联系人. */
    private static final java.util.Map<String, Set<String>> LOCKED = new java.util.HashMap<>();

    static {
        LOCKED.put("mm", new HashSet<>(java.util.Arrays.asList("微信", "我")));
        LOCKED.put("qq", new HashSet<>(java.util.Arrays.asList("消息", "联系人")));
        LOCKED.put("bili", new HashSet<>(java.util.Arrays.asList("首页", "我的")));
        LOCKED.put("heybox", new HashSet<>(java.util.Arrays.asList("首页", "我")));
    }

    /** Basics that the given host keeps, whatever the manager says. */
    static boolean isLockedTitle(String appKey, String t) {
        Set<String> set = LOCKED.get(appKey);
        return set != null && set.contains(t);
    }

    /** First non-empty TextView text inside a cell, else its position. */
    private static String cellTitle(View cell, int fallback) {
        if (cell instanceof ViewGroup) {
            String found = findText((ViewGroup) cell);
            if (found != null && !found.isEmpty()) {
                return found.toString().trim();
            }
        } else if (cell instanceof TextView) {
            CharSequence c = ((TextView) cell).getText();
            if (c != null && c.length() > 0) return c.toString().trim();
        }
        // Some bars label cells only via accessibility (TabWidget does).
        CharSequence cd = cell.getContentDescription();
        if (cd != null && cd.length() > 0) return cd.toString().trim();
        return "#" + fallback;
    }

    private static String findText(ViewGroup vg) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View c = vg.getChildAt(i);
            if (c instanceof TextView) {
                CharSequence t = ((TextView) c).getText();
                if (t != null && t.length() > 0) return t.toString();
            } else if (c instanceof ViewGroup) {
                String r = findText((ViewGroup) c);
                if (r != null) return r;
            }
        }
        return null;
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
            if (n < 2) {
                LiquidGlassModule.log(android.util.Log.WARN,
                        "tab layout skipped: app=" + appKey + " childCount=" + n);
                clear();
                return;
            }
            String[] titles = new String[n];
            for (int i = 0; i < n; i++) {
                titles[i] = cellTitle(row.getChildAt(i), i);
            }
            if (GlassConfig.tabOrder.length == 0 && GlassConfig.tabHidden.length == 0) {
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
                if (h != null && !isLockedTitle(appKey, h)) hidden.add(h);
            }
            hidden.remove(titles[0]);
            // Display order: configured titles first, then the rest natively.
            List<Integer> disp = new ArrayList<>();
            boolean[] used = new boolean[n];
            for (String want : GlassConfig.tabOrder) {
                if (want == null) continue;
                for (int i = 0; i < n; i++) {
                    if (!used[i] && want.equals(titles[i]) && !hidden.contains(titles[i])) {
                        used[i] = true;
                        disp.add(i);
                        break;
                    }
                }
            }
            for (int i = 0; i < n; i++) {
                if (!used[i] && !hidden.contains(titles[i])) disp.add(i);
            }
            // Hard guard: the default-entry tab (logical 0) always stays,
            // even when its runtime title matched nothing we know.
            if (!disp.contains(0)) disp.add(0, 0);
            List<View> cells = new ArrayList<>();
            for (int i = 0; i < n; i++) cells.add(row.getChildAt(i));
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
        if (m == null || row == null || bar != sRow) {
            return; // no rewrite in effect: native selection is already right
        }
        try {
            boolean fixed = false;
            for (int p = 0; p < row.getChildCount() && p < m.length; p++) {
                View cell = row.getChildAt(p);
                boolean want = m[p] == logicalIndex;
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
