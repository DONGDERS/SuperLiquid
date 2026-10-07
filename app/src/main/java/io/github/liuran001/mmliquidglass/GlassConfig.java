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
final class GlassConfig {

    private static final String MODULE_PKG = BuildConfig.APPLICATION_ID;
    private static final String REMOTE_NAME = "superliquid";

    /** Named before QQ was a target; kept so existing WeChat setups still read. */
    private static final String PREFS = "wx_liquid_glass_cfg";

    /** Distance between the bottom of the glass pill and the screen edge, dp. */
    static volatile int barOffsetDp = 12;
    /** Pill width multiplier, per app (bili default 0.7, others 1.0). */
    static volatile float widthScale = 1f;
    /** Region-buffer stack-blur radius for the glass sampling. */
    static volatile int blurRadius = 3;
    /** Multiplier over the built-in glass tint alphas (0.2 – 2.0). */
    static volatile float tintStrength = 1f;
    /** Whether the real Bilibili bottom bar is kept hidden under the glass. */
    static volatile boolean biliHideNative = true;
    /** Publish-pill size multiplier and bottom/right margins, dp. */
    static volatile float biliPubScale = 1f;
    static volatile int biliPubBottomDp = 12;
    static volatile int biliPubSideDp = 16;
    /** Fallback route when no live publish route was learned. */
    static volatile String biliPublishFallback = "bilibili://uper/center_plus";

    static volatile boolean enabled = true;

    private static volatile String keyPrefix = "";
    private static volatile boolean moduleLayerRead = false;

    private GlassConfig() {
    }

    /** App key for a package, or "" when unknown. */
    private static String prefixFor(String pkg) {
        if (HostApp.BILIBILI.pkg.equals(pkg)) return "bili";
        if (HostApp.WECHAT.pkg.equals(pkg)) return "mm";
        if (HostApp.QQ.pkg.equals(pkg)) return "qq";
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
        barOffsetDp = 12;
        widthScale = "bili".equals(keyPrefix) ? 0.7f : 1f;
        blurRadius = 3;
        tintStrength = 1f;
        biliHideNative = true;
        biliPubScale = 1f;
        biliPubBottomDp = 12;
        biliPubSideDp = 16;
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
        widthScale = p.getFloat(k("width_scale"), widthScale);
        blurRadius = p.getInt(k("blur"), blurRadius);
        tintStrength = p.getFloat(k("tint"), tintStrength);
        if ("bili".equals(keyPrefix)) {
            biliHideNative = p.getBoolean("bili_hide_native", biliHideNative);
            biliPubScale = p.getFloat("bili_pub_scale", biliPubScale);
            biliPubBottomDp = p.getInt("bili_pub_bottom_dp", biliPubBottomDp);
            biliPubSideDp = p.getInt("bili_pub_side_dp", biliPubSideDp);
            biliPublishFallback = p.getString(
                    "bili_publish_fallback", biliPublishFallback);
        }
        moduleLayerRead = true;
    }
}
