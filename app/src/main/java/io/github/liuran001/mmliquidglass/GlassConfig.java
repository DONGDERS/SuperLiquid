package io.github.liuran001.mmliquidglass;

import android.content.Context;
import android.content.SharedPreferences;

import java.lang.reflect.Constructor;

import io.github.libxposed.api.XposedInterface;

/**
 * Layered config store read inside each hooked host process.
 *
 * <p>Read priority (highest wins):
 * <ol>
 *   <li>LSPosed RemotePreferences "superliquid" — written by the manager app
 *       through XposedServiceHelper;</li>
 *   <li>XSharedPreferences on the module's own "superliquid" file — the same
 *       values mirrored by the manager into plain prefs, for setups where the
 *       LSPosed service channel is unavailable;</li>
 *   <li>the host's own legacy {@code wx_liquid_glass_cfg} file (barOffsetDp
 *       only — kept for pre-0.4.0 setups).</li>
 * </ol>
 *
 * <p>Every value is read under the per-app key prefix ("mm_", "qq_",
 * "bili_") because each host process only cares about its own app's knobs.
 * Key names are shared with the manager's Config object — keep both in sync.
 */
public final class GlassConfig {

    private static final String MODULE_PKG = BuildConfig.APPLICATION_ID;
    private static final String REMOTE_NAME = "superliquid";

    /** Named before QQ was a target; kept so existing WeChat setups still read. */
    private static final String PREFS = "wx_liquid_glass_cfg";

    /** Distance between the bottom of the glass pill and the screen edge, dp. */
    static volatile int barOffsetDp = 12;
    /** Horizontal offset of the glass pill from screen centre, dp (negative = left). */
    static volatile int barOffsetXDp = 0;
    /** Pill width multiplier, per app (bili default 0.7, others 1.0). */
    static volatile float widthScale = 1f;
    /** Region-buffer stack-blur radius for the glass sampling. */
    static volatile int blurRadius = 3;
    /** Whole-tab-bar scale (height+width+glyphs), applied to the host view. */
    static volatile float pillScale = 1f;
    /** Multiplier over the built-in glass tint alphas (0.2 – 2.0). */
    static volatile float tintStrength = 1f;
    /** Extra saturation on the sampled backdrop (refraction feel, 1.0 – 1.3). */
    static volatile float saturationBoost = 1f;
    /** Gloss highlight strength multiplier (0 – 2). */
    static volatile float glossStrength = 1f;
    /** Border stroke width multiplier (0.5 – 2). */
    static volatile float borderWidthScale = 1f;
    /** Whether the real Bilibili bottom bar is kept hidden under the glass. */
    /** Publish-pill size multiplier and bottom/right margins, dp. */
    static volatile float pubScale = 1f;
    static volatile int pubBottomDp = 12;
    static volatile int pubSideDp = 16;
    /** Fallback route when no live publish route was learned. */
    static volatile String biliPublishFallback = "bilibili://uper/center_plus";

    /** Floating-bar tab layout (ALL hosts): display order (titles) and hidden
     *  set, matched against each bar's tab titles. The manager writes these as
     *  JSON string arrays under <app>_tab_order / <app>_tab_hidden. */
    static volatile String[] tabOrder = new String[0];
    static volatile String[] tabHidden = new String[0];

    static volatile boolean enabled = true;
    public static final int DEFAULT_KUGOU_PLAYER_LIFT_DP = 20;
    static volatile int kugouPlayerLiftDp = DEFAULT_KUGOU_PLAYER_LIFT_DP;

    private static volatile String keyPrefix = "";
    private static volatile boolean moduleLayerRead = false;

    /** App key ("mm"/"qq"/"bili"/"heybox"), "" when unknown. */
    static String key() {
        return keyPrefix;
    }

    private GlassConfig() {
    }

    /** App key for a package, or "" when unknown. */
    private static String prefixFor(String pkg) {
        if (HostApp.BILIBILI.pkg.equals(pkg)) return "bili";
        if (HostApp.WECHAT.pkg.equals(pkg)) return "mm";
        if (HostApp.QQ.pkg.equals(pkg)) return "qq";
        if (HostApp.HEYBOX.pkg.equals(pkg)) return "heybox";
        if (HostApp.KUGOU.pkg.equals(pkg)) return "kugou";
        if (HostApp.PINDUODUO.pkg.equals(pkg)) return "pinduoduo";
        return "";
    }

    private static String k(String name) {
        String p = keyPrefix;
        return p.isEmpty() ? name : p + "_" + name;
    }

    /**
     * Wire the module-side channels. Called from onPackageLoaded, before any
     * install path can consume the values.
     */
    static void setModule(XposedInterface xp, String pkg) {
        keyPrefix = prefixFor(pkg);
        // Reset to per-app defaults before layering, so a prefix switch in a
        // long-lived process cannot inherit another app's values.
        enabled = true;
        kugouPlayerLiftDp = DEFAULT_KUGOU_PLAYER_LIFT_DP;
        barOffsetDp = 12;
        barOffsetXDp = 0;
        // r12 lesson (log-arbitrated): the handoff below OVERWRITES
        // HostApp.widthScale, so the per-app default must live HERE. "bili"
        // alone covered bilibili; heybox rode the same 0.7 shrink but got
        // reset to 1f — the hug then hit its screen cap and the bar went
        // full-width (tabWidth=246/total=1008 in the capture).
        widthScale = "bili".equals(keyPrefix) ? 0.7f
                : "heybox".equals(keyPrefix) ? 0.5f : 1f;
        blurRadius = 3;
        tintStrength = 1f;
        // These six were missing from the reset and would have survived a
        // prefix switch in a long-lived process, contradicting the contract
        // stated above (and the comment claiming a complete reset).
        pillScale = 1f;
        saturationBoost = 1f;
        glossStrength = 1f;
        borderWidthScale = 1f;
        tabOrder = new String[0];
        tabHidden = new String[0];
        pubScale = 1f;
        pubBottomDp = 12;
        pubSideDp = 16;
        biliPublishFallback = "bilibili://uper/center_plus";
        moduleLayerRead = false;

        try {
            apply(xp.getRemotePreferences(REMOTE_NAME));
        } catch (Throwable t) {
            LiquidGlassModule.logErr("config: remote prefs unavailable", t);
        }
        try {
            Class<?> cls = Class.forName("de.robv.android.xposed.XSharedPreferences");
            Constructor<?> ctor = cls.getConstructor(String.class, String.class);
            Object xsp = ctor.newInstance(BuildConfig.APPLICATION_ID, REMOTE_NAME);
            xsp.getClass().getMethod("reload").invoke(xsp);
            // No canRead() gate here: the module's prefs dir is not visible to
            // the host process, but LSPosed's XSharedPreferences reads it
            // daemon-side. canRead() from the host was always false, which
            // silently disabled this channel. Defaults are returned when the
            // file genuinely can't be read.
            apply((SharedPreferences) xsp);
        } catch (Throwable t) {
            // XSharedPreferences channel is optional; remote prefs or the
            // legacy host file still cover it.
        }
        // Width lives on HostApp, consumed by the installer's hug pass.
        try {
            HostApp app = HostApp.forPackage(pkg);
            if (app != null) {
                app.widthScale = widthScale;
            }
        } catch (Throwable t) {
            LiquidGlassModule.logErr("config: widthScale handoff failed", t);
        }
    }

    static void load(Context ctx) {
        try {
            SharedPreferences p = ctx.getSharedPreferences(PREFS, 0);
            if (!moduleLayerRead) {
                barOffsetDp = p.getInt("barOffsetDp", barOffsetDp);
            }
        } catch (Throwable t) {
            LiquidGlassModule.logErr("config load failed", t);
        }
    }

    private static void apply(SharedPreferences p) {
        enabled = p.getBoolean(k("enabled"), enabled);
        barOffsetDp = p.getInt(k("offset_dp"), barOffsetDp);
        barOffsetXDp = p.getInt(k("offsetx_dp"), barOffsetXDp);
        widthScale = p.getFloat(k("width_scale"), widthScale);
        pillScale = p.getFloat(k("pill_scale"), pillScale);
        blurRadius = p.getInt(k("blur"), blurRadius);
        tintStrength = p.getFloat(k("tint"), tintStrength);
        saturationBoost = p.getFloat(k("saturation"), saturationBoost);
        glossStrength = p.getFloat(k("gloss"), glossStrength);
        borderWidthScale = p.getFloat(k("border"), borderWidthScale);
        if ("kugou".equals(keyPrefix)) {
            kugouPlayerLiftDp = Math.max(0, Math.min(48, p.getInt(k("player_lift_dp"), DEFAULT_KUGOU_PLAYER_LIFT_DP)));
        }
        if ("bili".equals(keyPrefix) || "heybox".equals(keyPrefix)) {
            pubScale = p.getFloat(k("pub_scale"), pubScale);
            pubBottomDp = p.getInt(k("pub_bottom_dp"), pubBottomDp);
            pubSideDp = p.getInt(k("pub_side_dp"), pubSideDp);
        }
        if ("bili".equals(keyPrefix)) {
            biliPublishFallback = p.getString(
                    "bili_publish_fallback", biliPublishFallback);
        }
        if (!keyPrefix.isEmpty()) {
            tabOrder = readStrArray(p, keyPrefix + "_tab_order");
            tabHidden = readStrArray(p, keyPrefix + "_tab_hidden");
        }
        moduleLayerRead = true;
        // Full applied-config snapshot: one log line answers "why does the
        // glass look wrong" without guessing (adb logcat -s SuperLiquid).
        LiquidGlassModule.log(android.util.Log.INFO,
                "glass config applied: app=" + keyPrefix
                        + " enabled=" + enabled
                        + " width=" + widthScale + " pill=" + pillScale
                        + " offset=" + barOffsetDp + " offsetX=" + barOffsetXDp + " blur=" + blurRadius
                        + " tint=" + tintStrength + " sat=" + saturationBoost
                        + " gloss=" + glossStrength + " border=" + borderWidthScale
                        + " tabs(order=" + tabOrder.length + " hidden=" + tabHidden.length + ")");
    }

    /** Tolerant JSON string-array read: corrupt/missing/absent value → empty
     *  array (native order / nothing hidden). Never throws. */
    private static String[] readStrArray(SharedPreferences p, String key) {
        try {
            String s = p.getString(key, null);
            if (s == null || s.isEmpty()) return new String[0];
            org.json.JSONArray arr = new org.json.JSONArray(s);
            String[] out = new String[arr.length()];
            for (int i = 0; i < arr.length(); i++) out[i] = arr.getString(i);
            return out;
        } catch (Throwable t) {
            LiquidGlassModule.log(android.util.Log.WARN,
                    "config: " + key + " unreadable, keeping defaults");
            return new String[0];
        }
    }
}
