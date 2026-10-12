package io.github.liuran001.mmliquidglass;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

/**
 * Heybox (小黑盒, com.max.xiaoheihe) bottom bar, self-drawn pill style.
 *
 * <p>The home layout ships a classic View bar: a {@code RadioGroup} (id
 * {@code rg_main}) with five RadioButtons — the middle slot ({@code rb_3})
 * is GONE in xml and the publish button is a separate CardView ({@code
 * vg_mid_tab}) parked over it at runtime, plus a red-dot overlay strip
 * ({@code vg_tips}). Labels and icons are rebound at runtime from a
 * server-driven config ({@code NavBarCfgObj}/{@code AppTabsObj}), so the
 * static xml texts ("发现/游戏库/社区") do NOT match the live bar
 * ("首页/热点/游戏库/我") — everything the pills show is captured from the
 * live RadioButtons at install time, never bundled.
 *
 * <p>Strategy mirrors {@link BiliHomeBar}: hide the real bar in place, feed
 * self-drawn pills through the ordinary installer entry, drive the app by
 * performing clicks on the captured native views. Layout here is a triad
 * matching the stock bar — [2 tabs] [plus] [2 tabs] — centred like the
 * original, with the plus as its own glass pill (self-drawn strokes, no
 * background: heybox's own publish icon ships with a coloured plate that
 * looks wrong inside the glass).
 */
final class HeyboxHomeBar {

    private static final String HOST_PKG = "com.max.xiaoheihe";

    /** Poll cadence; mirrors the bili bar's selection sync. */
    private static final long POLL_MS = 250;
    /** Gap between the three pills, dp. */
    private static final int PILL_GAP_DP = 12;
    /** Publish pill box, dp (mirrors the bili publish pill). */
    private static final int PLUS_W_DP = 56;
    private static final int PLUS_H_DP = 52;
    /** Plus glyph stroke length/width inside the publish pill, dp. */
    private static final int PLUS_GLYPH_DP = 22;
    private static final float PLUS_STROKE_DP = 2.5f;

    private static final WeakReference<Activity> NULL_ACT = new WeakReference<>(null);

    private static WeakReference<Activity> sHomeActivity = NULL_ACT;
    private static WeakReference<RadioGroup> sRealBar = new WeakReference<>(null);
    private static WeakReference<View> sPublishView = new WeakReference<>(null);
    private static WeakReference<View> sTipsView = new WeakReference<>(null);
    private static WeakReference<LiquidGlassHostLayout> sLeftHost = new WeakReference<>(null);
    private static WeakReference<LiquidGlassHostLayout> sPlusHost = new WeakReference<>(null);
    private static WeakReference<LiquidGlassPanel> sPlusPanel = new WeakReference<>(null);

    private static RadioButton[] sRealTabs;
    private static ImageView[] sIcons;
    private static TextView[] sLabels;
    private static int sAccent;
    private static int sNormal;

    private static LinearLayout sLeftRow;

    private static volatile boolean sInstalled;
    private static volatile boolean sProbing;
    private static int sProbes;
    private static volatile int sLastIndex = -1;

    private static final Handler sMain = new Handler(Looper.getMainLooper());
    private static long sLastFailLog;
    private static int sResumeLogs;
    private static int sIconlessLogs;

    /** TextView.mDrawableResId — the resource behind drawableTop, if any. */
    private static int drawableResIdOf(TextView tv) {
        try {
            java.lang.reflect.Field f = TextView.class.getDeclaredField("mDrawableResId");
            f.setAccessible(true);
            return f.getInt(tv);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Live theme, tracked by poll off the left host (drives night tinting). */
    private static volatile boolean sDark;
    /** Night selected-icon tint (no night png ships for selected state). */
    private static final android.graphics.ColorFilter NIGHT_SELECTED_FILTER =
            new android.graphics.PorterDuffColorFilter(0xFFCCCDCE,
                    android.graphics.PorterDuff.Mode.SRC_ATOP);

    static void install(HostApp app, ClassLoader cl) {
        // Nothing to hook up front: the bar is found by view id on the live
        // tree (heybox resources are not name-obfuscated), and switching is
        // driven by performing clicks on captured native views. The class
        // loader is only needed if a future revision grows a hook.
        LiquidGlassModule.log(android.util.Log.INFO,
                "heybox: bridge armed (no upfront hooks)");
    }

    static void onHomeResume(Activity activity) {
        sHomeActivity = new WeakReference<>(activity);
        if (sResumeLogs < 5) {
            sResumeLogs++;
            LiquidGlassModule.log(android.util.Log.INFO,
                    "heybox: onHomeResume #" + sResumeLogs
                            + (sInstalled ? " (installed)" : " probing=" + sProbing));
        }
        if (!sInstalled && !sProbing) {
            scheduleProbe();
        }
    }

    // ---------------------------------------------------------------- probing

    private static void scheduleProbe() {
        sProbing = true;
        sProbes = 0;
        sMain.postDelayed(HeyboxHomeBar::probe, 600);
    }

    private static void retryProbe() {
        sMain.postDelayed(HeyboxHomeBar::probe, 400);
    }

    private static void probe() {
        try {
            Activity act = sHomeActivity.get();
            if (act == null || act.isFinishing() || act.isDestroyed()) {
                sProbing = false;
                return;
            }
            if (++sProbes > 40) {
                LiquidGlassModule.log(android.util.Log.WARN,
                        "heybox: bar not found after 40 probes, giving up");
                sProbing = false;
                return;
            }
            RadioGroup real = findBar(act);
            if (real == null) {
                retryProbe();
                return;
            }
            if (!tryInstall(act, real)) {
                retryProbe();
            }
        } catch (Throwable t) {
            LiquidGlassModule.logErr("heybox: probe failed", t);
            retryProbe();
        } finally {
            sProbing = false;
        }
    }

    /** id lookup first (resources keep semantic names), shape scan as fallback. */
    private static RadioGroup findBar(Activity act) {
        View root = act.getWindow().getDecorView();
        int id = root.getResources().getIdentifier("rg_main", "id", HOST_PKG);
        if (id != 0) {
            View byId = root.findViewById(id);
            if (byId instanceof RadioGroup) {
                return (RadioGroup) byId;
            }
        }
        return findRadioGroup(root);
    }

    private static RadioGroup findRadioGroup(View v) {
        if (v instanceof RadioGroup) {
            RadioGroup rg = (RadioGroup) v;
            int rb = 0;
            for (int i = 0; i < rg.getChildCount(); i++) {
                if (rg.getChildAt(i) instanceof RadioButton) {
                    rb++;
                }
            }
            if (rb >= 3) {
                return rg;
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                RadioGroup found = findRadioGroup(g.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**ViewById helper over the decor tree with the same id-first/fallback rule. */
    private static View findById(View root, String name) {
        int id = root.getResources().getIdentifier(name, "id", HOST_PKG);
        return id != 0 ? root.findViewById(id) : null;
    }

    // ---------------------------------------------------------------- install

    private static boolean tryInstall(Activity act, RadioGroup real) {
        try {
            // Capture BEFORE hiding: labels/icons/colors come from the live bar.
            int n = 0;
            for (int i = 0; i < real.getChildCount(); i++) {
                if (real.getChildAt(i) instanceof RadioButton
                        && real.getChildAt(i).getVisibility() == View.VISIBLE) {
                    n++;
                }
            }
            if (n < 2) {
                LiquidGlassModule.log(android.util.Log.WARN,
                        "heybox: only " + n + " visible tabs, waiting for server nav cfg");
                return false;
            }
            sRealTabs = new RadioButton[n];
            sIcons = new ImageView[n];
            sLabels = new TextView[n];
            int k = 0;
            for (int i = 0; i < real.getChildCount(); i++) {
                View c = real.getChildAt(i);
                if (c instanceof RadioButton && c.getVisibility() == View.VISIBLE) {
                    sRealTabs[k++] = (RadioButton) c;
                }
            }
            captureColors();

            View publish = findById(act.getWindow().getDecorView(), "vg_mid_tab");
            View tips = findById(act.getWindow().getDecorView(), "vg_tips");

            // Hide chrome. The real bar stays attached but GONE: its listener
            // graph keeps working for the performClick drive, and restoring is
            // a visibility flip (no lp juggling like the CoordinatorLayout case).
            real.setVisibility(View.GONE);
            if (publish != null) {
                publish.setVisibility(View.GONE);
            }
            if (tips != null) {
                tips.setVisibility(View.GONE);
            }
            sRealBar = new WeakReference<>(real);
            sPublishView = new WeakReference<>(publish);
            sTipsView = new WeakReference<>(tips);

            ViewGroup parent = (ViewGroup) real.getParent();
            View backdrop = findById(act.getWindow().getDecorView(), "fl_container");
            if (backdrop == null) {
                backdrop = parent;
            }

            // ---- one pill, all tabs — exactly bili's architecture: the
            // installer's proven path (hug / droplet / geometry) manages the
            // single row; the plus pill is a separate bili-style publish
            // button parked bottom-right. No side pill, no translationX math.
            sLeftRow = buildRow(act, 0, n);

            FrameLayout leftWrap = new FrameLayout(act);
            leftWrap.addView(sLeftRow, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));
            // The installer swaps tabView for its glass host IN PLACE — the
            // view must already be attached, or install() bails on the null
            // parent (r5's every-time "install refused"). Park it at the real
            // bar's index with a COPY of its layout params (never share the
            // object: the installer mutates width/margins on it) and force the
            // bottom-centre anchor so the triad's translationX math holds.
            int idx = parent.indexOfChild(real);
            ViewGroup.LayoutParams hostLp;
            // The parent IS the RelativeLayout root (vg_main_root). The real
            // bar's runtime lp is set programmatically and is NOT a
            // RelativeLayout.LayoutParams (r6 lesson: a foreign lp type loses
            // all rules and drops the host top-left), and
            // ViewGroup.generateLayoutParams returns rule-less params. Build
            // the anchor directly.
            if (parent instanceof android.widget.RelativeLayout) {
                android.widget.RelativeLayout.LayoutParams rlp =
                        new android.widget.RelativeLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.addRule(ALIGN_PARENT_BOTTOM);
                rlp.addRule(CENTER_HORIZONTAL);
                hostLp = rlp;
            } else {
                hostLp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            }
            parent.addView(leftWrap, idx, hostLp);
            // One ERROR line with the full install context: if install()
            // bails, this line plus its silent-return table (parent null /
            // already wrapped / backdrop null / index<0) pinpoints which one
            // without another blind round trip.
            LiquidGlassModule.log(android.util.Log.ERROR,
                    "heybox: pre-install parent=" + parent.getClass().getSimpleName()
                            + " idx=" + idx
                            + " backdrop=" + (backdrop != null
                            ? backdrop.getClass().getSimpleName() : "null")
                            + " lp=" + hostLp.getClass().getSimpleName());
            LiquidGlassInstaller.install(leftWrap);
            if (!(leftWrap.getParent() instanceof LiquidGlassHostLayout)) {
                LiquidGlassModule.log(android.util.Log.ERROR,
                        "heybox: install refused — leftWrap parent now "
                                + (leftWrap.getParent() != null
                                ? leftWrap.getParent().getClass().getName() : "null"));
                parent.removeView(leftWrap);
                restoreNative();
                sProbes = 0;
                return false;
            }
            LiquidGlassHostLayout leftHost = (LiquidGlassHostLayout) leftWrap.getParent();
            // The real bar sits EARLY in the xml (before the fullscreen
            // content container) — the host inherits that z slot and the
            // content draws right over the glass. Raise it to the top.
            leftHost.bringToFront();
            leftHost.getParent().requestLayout();
            sLeftHost = new WeakReference<>(leftHost);

            LiquidGlassHostLayout plusHost = buildPlusPill(act, backdrop, parent);
            sPlusHost = new WeakReference<>(plusHost);

            sInstalled = true;
            sDark = isNight(act);
            sLastIndex = checkedIndex(real);
            applySelection();
            startPolling();
            LiquidGlassModule.log(android.util.Log.INFO,
                    "heybox: pill installed, tabs=" + n
                            + " accent=" + String.format("#%06X", sAccent & 0xFFFFFF)
                            + " publish=" + (publish != null)
                            + " plus=" + (plusHost != null));
            return true;
        } catch (Throwable t) {
            LiquidGlassModule.logErr("heybox: install failed", t);
            restoreNative();
            return false;
        }
    }

    /** Accent = selected tab's label color; normal = any unselected tab's. */
    private static void captureColors() {
        sAccent = 0xFFFF6699; // fallback: heybox brand pink
        sNormal = 0xFF999999;
        for (RadioButton rb : sRealTabs) {
            int c = rb.getCurrentTextColor();
            if (rb.isChecked() || rb.isSelected()) {
                sAccent = c;
            } else {
                sNormal = c;
            }
        }
    }

    /**
     * Re-read icons and label colors off the live RadioButtons. Called on
     * theme flips: the app swaps in its night drawables / colors itself
     * (dark mode ships night-xxhdpi variants for every unselected glyph),
     * and the live views — not static lookups — are the source of truth.
     */
    private static void recaptureVisuals(Activity act) {
        try {
            if (sRealTabs == null || sIcons == null || sLabels == null) {
                return;
            }
            for (int i = 0; i < sRealTabs.length && i < sIcons.length; i++) {
                Drawable sel = sRealTabs[i].getCompoundDrawablesRelative()[1];
                if (sel == null) {
                    sel = sRealTabs[i].getCompoundDrawables()[1];
                }
                if (sel != null && sel.getConstantState() != null
                        && sIcons[i] != null) {
                    sIcons[i].setImageDrawable(
                            sel.getConstantState().newDrawable().mutate());
                }
            }
            captureColors();
            // PlusView draws from sAccent live; just nudge a repaint.
            LiquidGlassHostLayout plus = sPlusHost.get();
            if (plus != null) {
                plus.invalidate();
            }
            applySelection(true);
        } catch (Throwable t) {
            LiquidGlassModule.logErr("heybox: visual recapture failed", t);
        }
    }

    private static LinearLayout buildRow(Activity act, int from, int to) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        float d = act.getResources().getDisplayMetrics().density;
        for (int i = from; i < to; i++) {
            LinearLayout tab = new LinearLayout(act);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            int padH = Math.round(11 * d);
            int padV = Math.round(7 * d);
            tab.setPadding(padH, padV, padH, padV);

            ImageView icon = new ImageView(act);
            Drawable sel = sRealTabs[i].getCompoundDrawablesRelative()[1];
            if (sel == null) {
                sel = sRealTabs[i].getCompoundDrawables()[1];
            }
            int side = Math.round(21 * d);
            // getConstantState() is nullable for runtime-constructed drawables
            // (heybox's server-rebound tabs ship one) — a null receiver was
            // r3's NPE despite the sel!=null guard.
            if (sel != null && sel.getConstantState() != null) {
                // Own instance per pill slot; the selector carries heybox's
                // own normal/selected artwork, driven by setSelected().
                icon.setImageDrawable(sel.getConstantState().newDrawable().mutate());
                // Original icon geometry, capped like the bar itself draws it.
                int iw = sel.getIntrinsicWidth(), ih = sel.getIntrinsicHeight();
                if (iw > 0 && ih > 0) {
                    side = Math.min(Math.max(iw, ih), Math.round(28 * d));
                }
            } else {
                // Server-config rebind may relocate the icon. mDrawableResId
                // is what TextView keeps for drawableTop set from a resource;
                // resolving it ourselves survives any absolute/relative mix.
                int resId = drawableResIdOf(sRealTabs[i]);
                if (resId != 0) {
                    try {
                        sel = act.getResources().getDrawable(resId);
                        if (sel != null && sel.getConstantState() != null) {
                            icon.setImageDrawable(sel.getConstantState().newDrawable().mutate());
                        }
                        int iw = sel.getIntrinsicWidth(), ih = sel.getIntrinsicHeight();
                        if (iw > 0 && ih > 0) {
                            side = Math.min(Math.max(iw, ih), Math.round(28 * d));
                        }
                    } catch (Throwable t) {
                        LiquidGlassModule.logErr("heybox: resId icon failed", t);
                    }
                }
                if (icon.getDrawable() == null && sIconlessLogs < 4) {
                    sIconlessLogs++;
                    LiquidGlassModule.log(android.util.Log.ERROR,
                            "heybox: slot " + i + " iconless — text="
                                    + sRealTabs[i].getText()
                                    + " resId=0x" + Integer.toHexString(resId)
                                    + " bg=" + (sRealTabs[i].getBackground() != null
                                    ? sRealTabs[i].getBackground().getClass().getName() : "null")
                                    + " fg=" + (sRealTabs[i].getForeground() != null
                                    ? sRealTabs[i].getForeground().getClass().getName() : "null")
                                    + " cls=" + sRealTabs[i].getClass().getName());
                }
            }
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(side, side);
            iconLp.gravity = Gravity.CENTER_HORIZONTAL;
            tab.addView(icon, iconLp);
            sIcons[i] = icon;

            TextView label = new TextView(act);
            label.setText(sRealTabs[i].getText());
            // Exact size/color of the live tab (server nav cfg may differ per user).
            label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                    sRealTabs[i].getTextSize());
            label.setTextColor(sNormal);
            label.setIncludeFontPadding(false);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelLp.topMargin = Math.round(3 * d);
            labelLp.gravity = Gravity.CENTER_HORIZONTAL;
            tab.addView(label, labelLp);
            sLabels[i] = label;

            final int slot = i;
            tab.setOnLongClickListener(v -> true);
            tab.setOnClickListener(v -> {
                try {
                    sRealTabs[slot].performClick();
                    sLastIndex = slot;
                    applySelection();
                } catch (Throwable t) {
                    LiquidGlassModule.logErr("heybox: tab click failed", t);
                }
            });
            row.addView(tab);
        }
        return row;
    }

    /** The standalone publish pill: one glass host, one self-drawn plus. */
    private static LiquidGlassHostLayout buildPlusPill(Activity act, View backdropView,
                                                       ViewGroup parent) {
        try {
            float d = act.getResources().getDisplayMetrics().density;
            ViewGroup backdrop = (ViewGroup) backdropView; // fl_container is a FrameLayout
            LiquidGlassHostLayout host = new LiquidGlassHostLayout(act, backdrop, null);
            host.setupShadow(d, isNight(act));
            int shadowPad = host.shadowPad();

            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            row.setPadding(shadowPad, shadowPad, shadowPad, shadowPad);
            row.addView(new PlusView(act, PLUS_GLYPH_DP, PLUS_STROKE_DP),
                    new LinearLayout.LayoutParams(
                            Math.round(PLUS_GLYPH_DP * d), Math.round(PLUS_GLYPH_DP * d)));
            row.setOnClickListener(v -> openPublish(v));

            host.addView(row, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));

            if (android.os.Build.VERSION.SDK_INT >= LiquidGlassPanel.MIN_SDK) {
                LiquidGlassPanel glass = new LiquidGlassPanel(act, backdrop, d, isNight(act));
                sPlusPanel = new WeakReference<>(glass);
                host.addView(glass, 0, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                host.setGlassTuner(new LiquidGlassHostLayout.GlassTuner() {
                    @Override
                    public void onSize(int w, int h, float cornerRadius) {
                        glass.invalidate();
                    }

                    @Override
                    public void onTheme(boolean dark) {
                        glass.setTheme(dark);
                    }
                });
            }
            host.setClipChildren(false);
            host.setClipToPadding(false);
            park(host, parent, act);
            return host;
        } catch (Throwable t) {
            LiquidGlassModule.logErr("heybox: plus pill failed", t);
            return null;
        }
    }

    /**
     * Bottom-end park (bili's publish-pill position: bottom-right, 16dp side
     * margin), in the app's RelativeLayout root. Params are built directly —
     * generateLayoutParams returns rule-less params that pin the child
     * top-left (r6 lesson).
     */
    private static void park(LiquidGlassHostLayout host, ViewGroup parent, Activity act) {
        float d = act.getResources().getDisplayMetrics().density;
        int shadowPad = host.shadowPad();
        ViewGroup.LayoutParams lp;
        if (parent instanceof android.widget.RelativeLayout) {
            android.widget.RelativeLayout.LayoutParams rlp =
                    new android.widget.RelativeLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.addRule(ALIGN_PARENT_BOTTOM);
            rlp.addRule(ALIGN_PARENT_END);
            rlp.rightMargin = Math.round(16 * d) + shadowPad;
            rlp.bottomMargin =
                    Math.round(GlassConfig.barOffsetDp * d) - shadowPad + navInset(act);
            lp = rlp;
        } else {
            lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.END);
            ((FrameLayout.LayoutParams) lp).rightMargin = Math.round(16 * d) + shadowPad;
            ((FrameLayout.LayoutParams) lp).bottomMargin =
                    Math.round(GlassConfig.barOffsetDp * d) - shadowPad + navInset(act);
        }
        parent.addView(host, lp);
    }

    /** RelativeLayout rule values (java.lang.reflect, no compile-time need). */
    private static final int ALIGN_PARENT_BOTTOM = 12;
    private static final int ALIGN_PARENT_END = 11;
    private static final int CENTER_HORIZONTAL = 14;

    /** Self-drawn plus: plain strokes, tinted with the live accent color. */
    private static final class PlusView extends View {
        private final float mLen;
        private final float mStroke;
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        PlusView(android.content.Context ctx, float lenDp, float strokeDp) {
            super(ctx);
            float d = ctx.getResources().getDisplayMetrics().density;
            mLen = lenDp * d;
            mStroke = strokeDp * d;
            mPaint.setColor(0xFFFFFFFF);
            mPaint.setStrokeCap(Paint.Cap.ROUND);
            mPaint.setStrokeWidth(mStroke);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            mPaint.setColor(sAccent);
            float c = getWidth() / 2f;
            canvas.drawLine(c - mLen / 2, c, c + mLen / 2, c, mPaint);
            canvas.drawLine(c, c - mLen / 2, c, c + mLen / 2, mPaint);
        }
    }

    // ---------------------------------------------------------------- drive

    private static void openPublish(View v) {
        View publish = sPublishView.get();
        if (publish != null) {
            try {
                // Hidden-but-attached view: performClick still runs the app's
                // own listener, whatever publish flow it opens.
                publish.performClick();
                LiquidGlassModule.log(android.util.Log.INFO, "heybox: publish click");
                return;
            } catch (Throwable t) {
                LiquidGlassModule.logErr("heybox: publish click failed", t);
            }
        }
        LiquidGlassModule.log(android.util.Log.WARN, "heybox: no publish view captured");
    }

    private static void startPolling() {
        sMain.postDelayed(HeyboxHomeBar::poll, POLL_MS);
    }

    private static void poll() {
        if (!sInstalled) {
            return;
        }
        try {
            LiquidGlassHostLayout left = sLeftHost.get();
            LiquidGlassHostLayout plus = sPlusHost.get();
            boolean leftAlive = left != null && left.isAttachedToWindow();
            boolean plusAlive = plus == null || plus.isAttachedToWindow();
            if (!leftAlive || !plusAlive) {
                // Window died: installer resets itself on next resume; we rebuild.
                sInstalled = false;
                sLeftRow = null;
                sLeftHost = new WeakReference<>(null);
                sPlusHost = new WeakReference<>(null);
                sLastIndex = -1;
                scheduleProbe();
                return;
            }
            keepChromeHidden();

            // Theme flips: the host tracks the live config (bili-style); our
            // icons/labels re-capture off the app's own bar, which swaps in
            // its night drawables/colors (night normal pngs exist; selected
            // has none, so night selected is tinted off-white instead).
            boolean dark = left.isDark();
            if (dark != sDark) {
                sDark = dark;
                Activity act = sHomeActivity.get();
                if (act != null) {
                    recaptureVisuals(act);
                }
                LiquidGlassModule.log(android.util.Log.INFO,
                        "heybox: theme -> " + (dark ? "night" : "day"));
            }

            // Follow the app's own page state (back nav, deep links).
            RadioGroup real = sRealBar.get();
            if (real != null) {
                int idx = checkedIndex(real);
                if (idx >= 0 && idx != sLastIndex) {
                    sLastIndex = idx;
                    applySelection();
                }
            }
            // Publish pill follows the live theme like the bili one.
            LiquidGlassPanel panel = sPlusPanel.get();
            if (left != null && left.isAttachedToWindow() && panel != null) {
                panel.setTheme(left.isDark());
            }
        } catch (Throwable t) {
            long now = SystemClock.uptimeMillis();
            if (now - sLastFailLog > 5000) {
                sLastFailLog = now;
                LiquidGlassModule.logErr("heybox: poll failed", t);
            }
        }
        sMain.postDelayed(HeyboxHomeBar::poll, POLL_MS);
    }

    /** A theme pass may re-show hidden chrome; keep it down unless asked. */
    private static void keepChromeHidden() {
        View real = sRealBar.get();
        if (real != null && real.getVisibility() != View.GONE) {
            real.setVisibility(View.GONE);
        }
        View tips = sTipsView.get();
        if (tips != null && tips.getVisibility() != View.GONE) {
            tips.setVisibility(View.GONE);
        }
    }

    private static int checkedIndex(RadioGroup real) {
        int checked = real.getCheckedRadioButtonId();
        if (checked == -1 || sRealTabs == null) {
            return -1;
        }
        for (int i = 0; i < sRealTabs.length; i++) {
            if (sRealTabs[i].getId() == checked) {
                return i;
            }
        }
        return -1;
    }

    private static void applySelection() {
        applySelection(false);
    }

    private static void applySelection(boolean force) {
        applyRow(sLeftRow, 0, force);
    }

    /**
     * Selection + theme state onto the pill tabs. Icon coloring strategy
     * (pixel-verified against the shipped art):
     * <ul>
     *   <li>day: normal glyph #8C9196, selected glyph #14191E — the pngs
     *       already carry both, selector handles it, no filter;</li>
     *   <li>night: normal swaps to the night png (#858687) via live
     *       recapture, but selected ships NO night variant (it would stay
     *       #14191E ink — invisible on dark glass), so the selected icon is
     *       tinted off-white {@code #CCCDCE} (device-observed value).</li>
     * </ul>
     */
    private static void applyRow(LinearLayout row, int base, boolean force) {
        if (row == null) {
            return;
        }
        for (int i = 0; i < row.getChildCount(); i++) {
            View tab = row.getChildAt(i);
            int idx = base + i;
            boolean selected = idx == sLastIndex;
            if (!force && tab.isSelected() == selected) {
                continue;
            }
            tab.setSelected(selected);
            ImageView icon = sIcons[idx];
            icon.setSelected(selected);
            icon.setColorFilter(selected && sDark ? NIGHT_SELECTED_FILTER : null);
            sLabels[idx].setTextColor(selected ? sAccent : sNormal);
        }
    }

    private static void restoreNative() {
        try {
            View real = sRealBar.get();
            if (real != null) {
                real.setVisibility(View.VISIBLE);
            }
            View tips = sTipsView.get();
            if (tips != null) {
                tips.setVisibility(View.VISIBLE);
            }
            // vg_mid_tab stays GONE: xml ships it hidden; the app shows it
            // itself when the publish button is wanted.
        } catch (Throwable t) {
            LiquidGlassModule.logErr("heybox: restore failed", t);
        }
    }

    private static boolean isNight(android.content.Context ctx) {
        return (ctx.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private static int navInset(Activity act) {
        try {
            WindowInsets ins = act.getWindow().getDecorView().getRootWindowInsets();
            return ins != null ? ins.getSystemWindowInsetBottom() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }
}
