package io.github.liuran001.mmliquidglass;

import android.app.Activity;
import android.content.Context;
import android.graphics.PorterDuff;
import android.net.Uri;
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
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Bilibili's home bottom bar is Compose: {@code BottomTabComponent} renders
 * inside a {@code WindowAwareComposeView} handed out by
 * {@code HomeTabServiceImplV2.getRootView()} — a single node in the View tree,
 * with no per-tab children to reparent, tint or poll. None of the machinery
 * that borrows the app's own bar for WeChat and QQ can apply, so this bridge
 * takes the opposite route, iOS-26 style:
 *
 * <ul>
 *   <li>the Compose bar is hidden in place (its state machinery keeps
 *       running, it just stopped being the thing the user sees),</li>
 *   <li>a self-built row of real Views (bundled glyphs + labels, tagged with
 *       their slot) takes the ordinary {@link LiquidGlassInstaller#install}
 *       path and becomes the left, swipeable glass pill — every downstream
 *       piece (droplet, springs, backdrop refraction) works on it unchanged,</li>
 *   <li>a second, standalone glass pill with a pink plus button parks
 *       bottom-right and opens the publish sheet, mirroring the app's center
 *       publish button.</li>
 * </ul>
 *
 * <p>Switching goes through the service surface (9.13.0, interface
 * {@code v20.l} on {@code HomeTabServiceImplV2}): {@code p(Z, I, String,
 * View, Bundle)} activates the page at index {@code I}, {@code j()} reads the
 * current index back, and the same 150 ms view-state polling QQ uses keeps
 * the row in sync. Publish routing goes through {@code BLRouter}: the scheme
 * is server-configured, so the exact route the app itself dispatches is
 * observed and cached, with {@code bilibili://uper/center_plus} (matching the
 * {@code center_plus_type} parameter the Compose click handler appends) as
 * the cold-start fallback.
 */
final class BiliHomeBar {

    private static volatile boolean sHooked;
    private static volatile WeakReference<Object> sService = new WeakReference<>(null);
    private static volatile WeakReference<Activity> sHomeActivity = new WeakReference<>(null);
    private static volatile WeakReference<View> sWindowBar = new WeakReference<>(null);
    private static volatile WeakReference<View> sRealBar = new WeakReference<>(null);
    private static volatile WeakReference<ViewGroup> sRealParent = new WeakReference<>(null);
    private static volatile int sRealIdx = -1;
    private static volatile ViewGroup.LayoutParams sRealLp = null;
    private static volatile Method mSetItem;
    private static volatile Method mGetItem;
    /** Pill slot -> real-bar page index; the centre publish (2) is not a page. */
    private static final int[] SLOT_TO_APP = {0, 1, 3, 4};
    private static final String REAL_BAR_CLASS =
            "com.bilibili.lib.homepage.widget.TabHost";
    private static volatile LinearLayout sRow;
    private static volatile WeakReference<LiquidGlassHostLayout> sPublishHost =
            new WeakReference<>(null);
    private static volatile WeakReference<LiquidGlassHostLayout> sLeftHost =
            new WeakReference<>(null);
    private static volatile WeakReference<LiquidGlassPanel> sPublishPanel =
            new WeakReference<>(null);
    private static volatile View sComposeBar;
    private static volatile boolean sInstalled;
    private static volatile boolean sProbing;
    private static volatile boolean sPolling;
    private static final Handler sMain = new Handler(Looper.getMainLooper());

    private static Method mSwitch;
    private static Method mIndex;
    private static Method mCount;
    private static Method mRootView;
    private static Method mRouteTo;
    private static Method mToRouteRequest;
    private static volatile boolean mRouteToStatic;
    private static volatile boolean mRouteToNeedsInstance;
    private static volatile Object mRouterInstance;

    private static int sLastIndex = -1;
    private static int sProbes;
    private static long sLastIndexFailLog;
    private static long sLastSwitchFailLog;
    private static volatile String sPublishRoute;

    private static final String SERVICE_CLASS =
            "tv.danmaku.bili.home.service.HomeTabServiceImplV2";
    private static final String ROUTER_CLASS = "com.bilibili.lib.blrouter.BLRouter";
    private static final String ROUTE_REQ_KT = "com.bilibili.lib.blrouter.RouteRequestKt";
    private static final String HOST_PKG = "tv.danmaku.bili";
    private static final String MODULE_PKG = BuildConfig.APPLICATION_ID;
    private static final String PUBLISH_FALLBACK = "bilibili://uper/center_plus";

    private static final int GRAY = 0xFF61666D;
    private static final int PINK = 0xFFFB7299;
    private static final String[] LABELS = {"首页", "动态", "会员购", "我的"};
    private static final String[] ICON_NAMES = {"tab_home", "tab_dynamic", "tab_mall", "tab_mine"};

    private static final long POLL_MS = 150;
    private static final long PROBE_DELAY_MS = 800;
    private static final int PROBE_LIMIT = 40;

    private BiliHomeBar() {
    }

    // ---------------------------------------------------------------- hooks

    static void install(HostApp app, ClassLoader cl) {
        if (sHooked) {
            return;
        }
        sHooked = true;
        try {
            Class<?> svc = cl.loadClass(SERVICE_CLASS);
            LiquidGlassModule.hookAfter(svc.getDeclaredConstructor(), chain -> {
                Object thiz = chain.getThisObject();
                if (thiz != null) {
                    sService = new WeakReference<>(thiz);
                    // The one line that proves the hook can see the live
                    // service; its absence after onResume means a Tinker
                    // loader owns this class, not the one we hooked.
                    LiquidGlassModule.log(android.util.Log.INFO,
                            "bili: service instance captured");
                }
                sProbes = 0;
                scheduleProbe();
            });
            LiquidGlassModule.log(android.util.Log.INFO, "bili: hooked " + SERVICE_CLASS);
        } catch (Throwable t) {
            LiquidGlassModule.logErr("bili: service hook failed", t);
        }
        hookRouter(cl);
        hookWindowAdds();
    }

    /**
     * The bottom bar never enters the activity's view tree — it is a window of
     * its own (hence the name WindowAwareComposeView), added through the
     * framework WindowManager. Hooking the framework side is loader-proof: it
     * sees the live view whatever classloader built it. The same view carries
     * the component that holds the service, so this is both the hide target
     * and the service source.
     */
    private static void hookWindowAdds() {
        try {
            Class<?> global = Class.forName("android.view.WindowManagerGlobal");
            int hooked = 0;
            for (Method m : global.getDeclaredMethods()) {
                if (!"addView".equals(m.getName())) {
                    continue;
                }
                Class<?>[] ps = m.getParameterTypes();
                if (ps.length < 1 || !View.class.isAssignableFrom(ps[0])) {
                    continue;
                }
                m.setAccessible(true);
                LiquidGlassModule.hookAfter(m, chain -> {
                    Object v = chain.getArg(0);
                    if (!(v instanceof View)) {
                        return;
                    }
                    String n = v.getClass().getName();
                    if (n.startsWith("tv.danmaku.bili.home.components")
                            || n.endsWith("WindowAwareComposeView")) {
                        View bar = (View) v;
                        if (sWindowBar == null || sWindowBar.get() != bar) {
                            LiquidGlassModule.log(android.util.Log.INFO,
                                    "bili: bar window captured: " + n);
                            dumpFields(bar);
                        }
                        sWindowBar = new WeakReference<>(bar);
                        if (sService.get() == null) {
                            sProbes = 0;
                            scheduleProbe();
                        }
                    }
                });
                hooked++;
            }
            LiquidGlassModule.log(android.util.Log.INFO,
                    "bili: watching " + hooked + " addView overloads");
        } catch (Throwable t) {
            LiquidGlassModule.logErr("bili: window hook failed", t);
        }
    }

    /** One-shot field map of a captured view, for locating the service by eye. */
    private static void dumpFields(View v) {
        StringBuilder sb = new StringBuilder();
        Class<?> k = v.getClass();
        while (k != null && k != Object.class && k != android.view.View.class) {
            for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(f.getName()).append(':').append(f.getType().getSimpleName());
            }
            k = k.getSuperclass();
        }
        String s = sb.toString();
        int chunks = (s.length() + 1199) / 1200;
        for (int i = 0, pos = 0; i < chunks; i++, pos += 1200) {
            LiquidGlassModule.log(android.util.Log.INFO,
                    "bili: barView fields[" + i + "/" + chunks + "]="
                            + s.substring(pos, Math.min(s.length(), pos + 1200)));
        }
    }

    /**
     * Observes every route the app itself dispatches; when a publish-shaped
     * scheme passes by (opened from anywhere in the app), it is cached so the
     * pill can replay the exact same request. Also resolves the dispatcher
     * pair used for replay.
     */
    private static void hookRouter(ClassLoader cl) {
        try {
            Class<?> router = cl.loadClass(ROUTER_CLASS);
            int hooked = 0;
            for (Method m : router.getDeclaredMethods()) {
                if ("routeTo".equals(m.getName())) {
                    m.setAccessible(true);
                    LiquidGlassModule.hookAfter(m, chain -> {
                        for (Object arg : chain.getArgs()) {
                            if (arg == null) {
                                continue;
                            }
                            String s = arg.toString();
                            if (s.contains("bilibili://") && publishShaped(s)) {
                                sPublishRoute = s;
                            }
                        }
                    });
                    // Replay prefers the simplest single-argument overload.
                    if (mRouteTo == null && m.getParameterTypes().length == 1) {
                        mRouteTo = m;
                        mRouteToStatic = Modifier.isStatic(m.getModifiers());
                        mRouteToNeedsInstance = !mRouteToStatic;
                    }
                    hooked++;
                }
            }
            if (mRouteTo != null && mRouteToNeedsInstance) {
                try {
                    mRouterInstance = router.getField("INSTANCE").get(null);
                } catch (Throwable ignored) {
                }
            }
            try {
                Class<?> reqKt = cl.loadClass(ROUTE_REQ_KT);
                for (Method m : reqKt.getDeclaredMethods()) {
                    if ("toRouteRequest".equals(m.getName())
                            && m.getParameterTypes().length == 1) {
                        m.setAccessible(true);
                        mToRouteRequest = m;
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
            LiquidGlassModule.log(android.util.Log.INFO,
                    "bili: watching " + hooked + " routeTo overloads, replay="
                            + (mRouteTo != null) + " build=" + (mToRouteRequest != null));
        } catch (Throwable t) {
            LiquidGlassModule.logErr("bili: router hook failed", t);
        }
    }

    private static boolean publishShaped(String s) {
        return s.contains("center_plus") || s.contains("following/publish")
                || s.contains("compose") || s.contains("publish");
    }

    // ---------------------------------------------------------------- probe

    private static void scheduleProbe() {
        sMain.postDelayed(BiliHomeBar::probe, PROBE_DELAY_MS);
    }

    /** The home activity is alive again; the tree walk may reach the service. */
    static void onHomeResume(Activity activity) {
        sHomeActivity = new WeakReference<>(activity);
        if (!sInstalled && !sProbing) {
            scheduleProbe();
        }
    }

    /**
     * Fallback capture: the ctor hook watches the class from the loader that
     * resolved at package load, but bilibili ships Tinker — the live service
     * may be an instance of a same-named class from a different loader, which
     * our hook never sees. The view tree always exists, though: walk it for
     * the bottom-tab component / its Compose view and mine the service out of
     * the component's fields.
     */
    private static Object captureServiceFromTree(Activity activity) {
        if (activity == null) {
            return null;
        }
        View root = activity.getWindow().getDecorView();
        View found = findByClass(root,
                "tv.danmaku.bili.home.components.bottomtab.BottomTabComponent");
        if (found == null) {
            View compose = findByClass(root,
                    "tv.danmaku.bili.home.components.bottomtab.WindowAwareComposeView");
            if (compose != null) {
                LiquidGlassModule.log(android.util.Log.INFO,
                        "bili: tree walk found compose view, climbing for component");
                for (View p = compose; p != null; ) {
                    if (p.getClass().getName().startsWith(
                            "tv.danmaku.bili.home.components")) {
                        found = p;
                        break;
                    }
                    p = p.getParent() instanceof View ? (View) p.getParent() : null;
                }
            }
        } else {
            LiquidGlassModule.log(android.util.Log.INFO,
                    "bili: tree walk found bottom tab component");
        }
        if (found == null) {
            return null;
        }
        // Scan fields for an instance of the service, matched by class NAME
        // so a cross-loader instance still counts.
        Class<?> k = found.getClass();
        while (k != null && k != Object.class) {
            for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                f.setAccessible(true);
                try {
                    Object v = f.get(found);
                    Object svc = findServiceIn(v, 0);
                    if (svc != null) {
                        return svc;
                    }
                } catch (Throwable ignored) {
                }
            }
            k = k.getSuperclass();
        }
        LiquidGlassModule.log(android.util.Log.WARN,
                "bili: component found but no service field in it");
        return null;
    }

    /** Depth-first hunt for a same-named service instance inside an object graph. */
    private static Object findServiceIn(Object o, int depth) {
        if (o == null || depth > 3) {
            return null;
        }
        if (o.getClass().getName().equals(SERVICE_CLASS)) {
            return o;
        }
        if (o instanceof View || o instanceof java.util.Map) {
            // component fields only; no graph-wide walk (too broad, can hit
            // enormous structures)
            Class<?> k = o.getClass();
            while (k != null && k != Object.class) {
                for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    if (f.getType().isPrimitive()) {
                        continue;
                    }
                    f.setAccessible(true);
                    try {
                        Object v = f.get(o);
                        if (v == o) {
                            continue;
                        }
                        Object hit = findServiceIn(v, depth + 1);
                        if (hit != null) {
                            return hit;
                        }
                    } catch (Throwable ignored) {
                    }
                }
                k = k.getSuperclass();
            }
        }
        return null;
    }

    /** First view in the tree whose class name matches exactly. */
    private static View findByClass(View root, String name) {
        if (name.equals(root.getClass().getName())) {
            return root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View hit = findByClass(vg.getChildAt(i), name);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    private static void retryProbe() {
        sProbes++;
        if (sProbes <= PROBE_LIMIT) {
            scheduleProbe();
        } else {
            LiquidGlassModule.log(android.util.Log.WARN,
                    "bili: bar probe gave up after " + PROBE_LIMIT + " rounds");
        }
    }

    private static void probe() {
        if (sProbing) {
            return;
        }
        sProbing = true;
        try {
            // ---- drive the real bar, never the Compose service
            // The home that actually ships for this account is the legacy
            // View TabHost (server-switched; the Compose variant our other
            // probes watch stays dormant). The TabHost lives in the activity
            // tree, its class is loader-correct by construction, and its
            // setCurrentItem/getCurrentItem pair is all the switching we
            // need. Hide it in place, take its slot in the parent, and hand
            // the pill through the ordinary installer.
            if (sInstalled || LiquidGlassInstaller.currentPager() != null) {
                return; // already up; the poller owns lifecycle from here
            }
            Activity act = sHomeActivity.get();
            if (act == null || act.isFinishing()) {
                retryProbe();
                return;
            }
            View real = findByClass(act.getWindow().getDecorView(), REAL_BAR_CLASS);
            if (real == null || !(real.getParent() instanceof ViewGroup)) {
                // home content not attached yet
                if (sProbes == 1 || sProbes % 5 == 0) {
                    LiquidGlassModule.log(android.util.Log.INFO,
                            "bili probe #" + sProbes + ": TabHost not in tree yet");
                }
                retryProbe();
                return;
            }
            ViewGroup parent = (ViewGroup) real.getParent();
            int idx = parent.indexOfChild(real);
            ViewGroup.LayoutParams originalLp = real.getLayoutParams();

            // Switching surface, resolved on the live class: loader-proof.
            try {
                mSetItem = method(real.getClass(), "setCurrentItem", int.class);
                mGetItem = method(real.getClass(), "getCurrentItem");
            } catch (Throwable t) {
                LiquidGlassModule.logErr("bili: TabHost switch surface missing", t);
            }

            // ---- left pill: the self-drawn 4-slot row through the ordinary path
            LinearLayout row = buildRow(act);
            FrameLayout barView = new FrameLayout(act);
            barView.addView(row, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));
            sRow = row;
            // The real bar yields its place AND its LayoutParams object: the
            // parent is a CoordinatorLayout, whose children must carry its own
            // param class (a generic FrameLayout.LayoutParams dies at the
            // first measure with a ClassCastException in prepareChildren).
            parent.removeView(real);
            parent.addView(barView, idx, originalLp);

            LiquidGlassInstaller.install(barView);
            if (!(barView.getParent() instanceof LiquidGlassHostLayout)) {
                LiquidGlassModule.log(android.util.Log.WARN,
                        "bili: left pill install refused, real bar restored");
                parent.removeView(barView);
                parent.addView(real, idx, originalLp);
                sRow = null;
                retryProbe();
                return;
            }
            LiquidGlassHostLayout leftHost = (LiquidGlassHostLayout) barView.getParent();
            sInstalled = true;
            sLeftHost = new WeakReference<>(leftHost);
            sRealBar = new WeakReference<>(real);
            sRealParent = new WeakReference<>(parent);
            sRealIdx = idx;
            sRealLp = originalLp;
            applyNativeBarVisibility();
            // Whole-tab-bar size knob, pivot bottom-centre.
            final float lps = GlassConfig.pillScale;
            if (lps != 1f) {
                final android.view.View leftHostF = leftHost;
                leftHost.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (leftHostF.getWidth() == 0 || leftHostF.getHeight() == 0) {
                            return;
                        }
                        leftHostF.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        try {
                        leftHostF.setPivotY(leftHostF.getHeight());
                        leftHost.setPivotX(leftHost.getWidth() / 2f);
                        leftHostF.setScaleX(lps);
                        leftHostF.setScaleY(lps);
                        } catch (Throwable t) {
                            LiquidGlassModule.logErr("bili pill scale failed", t);
                        }
                    }
                });
            }

            // Width is owned by the installer's hug (hugContentWidth → per-tab
            // column rewrite). Pinning it again here from row.getMeasuredWidth()
            // read the post-hug width and overwrote the capped value with
            // 1092px on a 1080px screen — the "full-width bar" bug. Hands off.
            nudgeStart(leftHost, act);

            // ---- right pill: standalone glass publish button
            LiquidGlassHostLayout publish = buildPublishPill(
                    act, LiquidGlassInstaller.currentPager(), parent);
            if (publish != null) {
                sPublishHost = new WeakReference<>(publish);
            }

            applySelection(row, 0);
            startPolling();
            LiquidGlassModule.log(android.util.Log.INFO,
                    "bili: two-pill install complete, publish="
                            + (publish != null ? "ok" : "skipped")
                            + " route=" + sPublishRoute);
        } catch (Throwable t) {
            LiquidGlassModule.logErr("bili: probe failed", t);
            retryProbe();
        } finally {
            sProbing = false;
        }
    }

    /**
     * Centre the tab pill horizontally (user preference over the iOS-26
     * bottom-start look). Only touches parents whose params we have a
     * contract with; anything else keeps the installer's own centring.
     */
    private static void nudgeStart(LiquidGlassHostLayout host, Activity act) {
        try {
            ViewGroup.LayoutParams lp = host.getLayoutParams();
            float d = act.getResources().getDisplayMetrics().density;
            if (lp instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                flp.gravity = Gravity.BOTTOM | Gravity.START;
                flp.leftMargin = Math.round(16 * d);
                flp.rightMargin = 0;
                host.setLayoutParams(flp);
                return;
            }
            // bilibili: the home root is a CoordinatorLayout, whose children
            // position through their own LayoutParams class. Both gravity and
            // the anchor that pinned the old bar are settable by name.
            Class<?> c = lp.getClass();
            while (c != null && c != Object.class) {
                if (c.getName().endsWith("CoordinatorLayout$LayoutParams")) {
                    java.lang.reflect.Field g = c.getField("gravity");
                    g.setInt(lp, Gravity.BOTTOM | Gravity.START);
                    java.lang.reflect.Field anchor = c.getField("anchorId");
                    if (anchor.getInt(lp) != View.NO_ID) {
                        anchor.setInt(lp, View.NO_ID);
                    }
                    if (lp instanceof ViewGroup.MarginLayoutParams) {
                        ((ViewGroup.MarginLayoutParams) lp).leftMargin = Math.round(16 * d);
                    }
                    break;
                }
                c = c.getSuperclass();
            }
            host.setLayoutParams(lp);
        } catch (Throwable t) {
            LiquidGlassModule.logErr("bili: repositioning left pill failed", t);
        }
    }

    // ---------------------------------------------------------------- right pill

    /**
     * The standalone publish button: one glass host, one pink plus. Parked in
     * the same parent as the left pill so both sample the same page container
     * through the same coordinate mapping — a host one level up samples a
     * shifted region and reads darker.
     */
    private static LiquidGlassHostLayout buildPublishPill(Activity act, ViewGroup backdrop,
                                                          ViewGroup parent) {
        try {
            if (backdrop == null) {
                LiquidGlassModule.log(android.util.Log.WARN,
                        "bili: no page container for the publish pill glass, skipping");
                return null;
            }
            Context ctx = act;
            float d = ctx.getResources().getDisplayMetrics().density;
            boolean night = isNight(ctx);
            // Manager-side pill tuning: overall scale + bottom/right offsets.
            float ps = GlassConfig.biliPubScale;
            if (ps < 0.5f) ps = 0.5f;
            if (ps > 2f) ps = 2f;

            LiquidGlassHostLayout host = new LiquidGlassHostLayout(ctx, backdrop, null);
            host.setupShadow(d, night);
            int shadowPad = host.shadowPad();

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            int pad = Math.round(15 * d);
            row.setPadding(pad, Math.round(13 * d), pad, Math.round(13 * d));

            android.graphics.drawable.Drawable glyph = moduleDrawable(ctx, "tab_publish");
            if (glyph == null) {
                LiquidGlassModule.log(android.util.Log.WARN,
                        "bili: plus glyph missing, publish pill skipped");
                return null;
            }
            ImageView icon = new ImageView(ctx);
            icon.setImageDrawable(glyph);
            icon.setColorFilter(PINK, PorterDuff.Mode.SRC_ATOP);
            int side = Math.round(26 * d * ps);
            row.addView(icon, new LinearLayout.LayoutParams(side, side));
            row.setOnClickListener(v -> openPublish(v));

            host.addView(row, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));

            if (android.os.Build.VERSION.SDK_INT >= LiquidGlassPanel.MIN_SDK) {
                LiquidGlassPanel glass = new LiquidGlassPanel(ctx, backdrop, d, night);
                sPublishPanel = new WeakReference<>(glass);
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

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    Math.round(56 * d * ps) + shadowPad * 2,
                    Math.round(52 * d * ps) + shadowPad * 2,
                    Gravity.BOTTOM | Gravity.END);
            lp.bottomMargin = Math.round(GlassConfig.biliPubBottomDp * d) - shadowPad + navInset(act);
            lp.rightMargin = Math.round(GlassConfig.biliPubSideDp * d);

            // Park beside the left pill in the app's own root, not in the
            // content root: the glass samples the page container, and a host
            // parked one level up samples a region whose mapping misses the
            // page (dark-mode base fill 0xFF111111 shows through = a darker
            // pill). Parent-generated params keep the CoordinatorLayout happy.
            // generateLayoutParams(ViewGroup.LayoutParams) is protected on
            // ViewGroup and only public on the concrete parent — reflect it.
            ViewGroup.LayoutParams glp = lp;
            try {
                java.lang.reflect.Method gen = parent.getClass().getMethod(
                        "generateLayoutParams", ViewGroup.LayoutParams.class);
                glp = (ViewGroup.LayoutParams) gen.invoke(parent, lp);
            } catch (Throwable ignored) {
                // keep the FrameLayout lp as-is (FrameLayout parents)
            }
            try {
                java.lang.reflect.Field g = glp.getClass().getField("gravity");
                g.setInt(glp, Gravity.BOTTOM | Gravity.END);
            } catch (Throwable ignored) {
                // FrameLayout-style parents carry gravity natively.
                if (glp instanceof FrameLayout.LayoutParams) {
                    ((FrameLayout.LayoutParams) glp).gravity =
                            Gravity.BOTTOM | Gravity.END;
                }
            }
            if (glp instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) glp).bottomMargin = lp.bottomMargin;
                ((ViewGroup.MarginLayoutParams) glp).rightMargin = lp.rightMargin;
            }
            parent.addView(host, glp);
            return host;
        } catch (Throwable t) {
            LiquidGlassModule.logErr("bili: publish pill failed", t);
            return null;
        }
    }

    private static int navInset(Activity act) {
        try {
            WindowInsets ins = act.getWindow().getDecorView().getRootWindowInsets();
            return ins != null ? ins.getSystemWindowInsetBottom() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- publish

    private static void openPublish(View v) {
        String target = sPublishRoute != null ? sPublishRoute : GlassConfig.biliPublishFallback;
        try {
            if (mRouteTo != null) {
                Object arg = buildRouteArg(target);
                if (arg != null) {
                    Object recv = mRouteToStatic
                            ? null
                            : (mRouterInstance != null ? mRouterInstance : null);
                    mRouteTo.invoke(recv, arg);
                    LiquidGlassModule.log(android.util.Log.INFO,
                            "bili: publish -> " + target);
                    return;
                }
            }
            // No router plumbing resolved: dispatch the Uri through the app.
            Context ctx = v.getContext();
            android.content.Intent it = new android.content.Intent(
                    android.content.Intent.ACTION_VIEW, Uri.parse(target));
            it.setPackage(HOST_PKG);
            ctx.startActivity(it);
            LiquidGlassModule.log(android.util.Log.WARN,
                    "bili: publish via raw intent (" + target + ")");
        } catch (Throwable t) {
            long now = SystemClock.uptimeMillis();
            if (now - sLastSwitchFailLog > 5000) {
                sLastSwitchFailLog = now;
                LiquidGlassModule.logErr("bili: publish open failed", t);
            }
        }
    }

    /** Builds routeTo's argument in whatever shape its signature wants. */
    private static Object buildRouteArg(String uri) {
        Class<?> p = mRouteTo.getParameterTypes()[0];
        if (p == String.class) {
            return uri;
        }
        if (p == Uri.class) {
            return Uri.parse(uri);
        }
        if (mToRouteRequest != null) {
            try {
                Object recv = Modifier.isStatic(mToRouteRequest.getModifiers()) ? null : null;
                Class<?> first = mToRouteRequest.getParameterTypes()[0];
                return mToRouteRequest.invoke(recv,
                        first == String.class ? uri : Uri.parse(uri));
            } catch (Throwable t) {
                LiquidGlassModule.logErr("bili: toRouteRequest failed", t);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- polling

    private static void startPolling() {
        if (sPolling) {
            return;
        }
        sPolling = true;
        sMain.postDelayed(BiliHomeBar::poll, POLL_MS);
    }

    private static void poll() {
        if (!sInstalled) {
            sPolling = false;
            return;
        }
        try {
            LinearLayout row = sRow;
            LiquidGlassHostLayout publish = sPublishHost.get();
            boolean alive = row != null && row.isAttachedToWindow();
            boolean publishAlive = publish == null || publish.isAttachedToWindow();
            if (!alive || !publishAlive) {
                // Activity recreated (or the window died): the installer's own
                // resume path resets its state; tear ours down and rebuild.
                sInstalled = false;
                sPolling = false;
                sLastIndex = -1;
                sRow = null;
                sPublishHost = new WeakReference<>(null);
                sProbes = 0;
                scheduleProbe();
                return;
            }
            // Keep the native bar out of the way unless the manager asks to
            // keep it (a skin/theme pass may re-show it).
            applyNativeBarVisibility();
            // The publish pill follows the left pill's live theme — its own
            // one-shot isNight() read at build time goes stale when the app
            // flips its skin later, and an inverted glass reads way off.
            LiquidGlassHostLayout leftHost = sLeftHost.get();
            LiquidGlassPanel panel = sPublishPanel.get();
            if (leftHost != null && leftHost.isAttachedToWindow()
                    && panel != null) {
                panel.setTheme(leftHost.isDark());
            }
            // Follow the app's own page state (back navigation, deep links).
            View real = sRealBar.get();
            if (real != null && mGetItem != null) {
                int slot = appToSlot(readIndex(real));
                if (slot >= 0 && slot != sLastIndex) {
                    sLastIndex = slot;
                    applySelection(row, slot);
                }
            }
        } catch (Throwable t) {
            LiquidGlassModule.logErr("bili: poll failed", t);
        }
        sMain.postDelayed(BiliHomeBar::poll, POLL_MS);
    }

    /** Reads getCurrentItem off the live bar; -1 when it will not answer. */
    private static int readIndex(View real) {
        try {
            int v = (Integer) mGetItem.invoke(real);
            if (v >= 0 && v < SLOT_TO_APP.length + 1) {
                return v;
            }
        } catch (Throwable t) {
            long now = SystemClock.uptimeMillis();
            if (now - sLastIndexFailLog > 5000) {
                sLastIndexFailLog = now;
                LiquidGlassModule.logErr("bili: index read failed", t);
            }
        }
        return -1;
    }

    /** Page index -> pill slot; the centre publish index keeps the last slot. */
    private static int appToSlot(int appIndex) {
        if (appIndex < 0) {
            return -1;
        }
        for (int s = 0; s < SLOT_TO_APP.length; s++) {
            if (SLOT_TO_APP[s] == appIndex) {
                return s;
            }
        }
        return -1; // publish index (2) or out of range: keep the last state
    }

    private static void applySelection(LinearLayout row, int index) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View tab = row.getChildAt(i);
            boolean selected = i == index;
            if (tab.isSelected() == selected) {
                continue;
            }
            tab.setSelected(selected);
            int color = selected ? PINK : GRAY;
            View icon = ((ViewGroup) tab).getChildAt(0);
            View label = ((ViewGroup) tab).getChildAt(1);
            ((ImageView) icon).setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
            ((TextView) label).setTextColor(color);
        }
    }

    // ---------------------------------------------------------------- building

    private static LinearLayout buildRow(Context ctx) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int d = Math.round(ctx.getResources().getDisplayMetrics().density);
        for (int i = 0; i < ICON_NAMES.length; i++) {
            LinearLayout tab = new LinearLayout(ctx);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            int padH = Math.round(5 * d);
            int padV = Math.round(5 * d);
            tab.setPadding(padH, padV, padH, padV);
            tab.setTag(i);

            ImageView icon = new ImageView(ctx);
            android.graphics.drawable.Drawable glyph = moduleDrawable(ctx, ICON_NAMES[i]);
            if (glyph != null) {
                icon.setImageDrawable(glyph);
            }
            icon.setColorFilter(GRAY, PorterDuff.Mode.SRC_ATOP);
            int side = Math.round(19 * d);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(side, side);
            iconLp.gravity = Gravity.CENTER_HORIZONTAL;
            tab.addView(icon, iconLp);

            TextView label = new TextView(ctx);
            label.setText(LABELS[i]);
            label.setTextSize(8);
            label.setTextColor(GRAY);
            label.setIncludeFontPadding(false);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelLp.topMargin = Math.round(2 * d);
            labelLp.gravity = Gravity.CENTER_HORIZONTAL;
            tab.addView(label, labelLp);

            final int index = i;
            // Long-press is an unused gesture on the pill: consume it so a
            // held press can't fire the tab click on release (the refresh
            // re-tap path must trigger on a clean tap only).
            tab.setOnLongClickListener(v -> true);
            tab.setOnClickListener(v -> {
                int appIndex = SLOT_TO_APP[index];
                View real = sRealBar.get();
                Integer cur = real == null ? null : Integer.valueOf(readIndex(real));
                if (appIndex == 0 && cur != null && cur == 0) {
                    // Already on home: re-tap means "refresh", not "switch".
                    // Hand the click to the native bar's own home tab so the
                    // app runs its stock refresh path (scroll top + reload).
                    refreshHomeFeed(real);
                } else {
                    switchTo(appIndex);
                }
                sLastIndex = index;
                applySelection(row, index);
            });
            row.addView(tab);
        }
        return row;
    }

    /**
     * Tab glyphs ship in the module APK's assets/ — resource-shrink-proof.
     * res/drawable copies were stripped by shrinkResources (they are only
     * referenced via getIdentifier, invisible to the shrinker), which is how
     * 0.4.2 lost every pill icon.
     */
    private static android.graphics.drawable.Drawable moduleDrawable(Context ctx, String name) {
        try {
            Context moduleCtx = ctx.createPackageContext(MODULE_PKG,
                    Context.CONTEXT_IGNORE_SECURITY);
            java.io.InputStream is = moduleCtx.getAssets().open(name + ".png");
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(is);
            is.close();
            if (bmp == null) {
                return null;
            }
            return new android.graphics.drawable.BitmapDrawable(
                    moduleCtx.getResources(), bmp);
        } catch (Throwable t) {
            LiquidGlassModule.logErr("bili: glyph " + name + " load failed", t);
            return null;
        }
    }

    // ---------------------------------------------------------------- service

    private static boolean resolveMethods(Class<?> svc) {
        if (mSwitch != null) {
            return true;
        }
        try {
            mSwitch = method(svc, "p",
                    boolean.class, int.class, String.class, View.class, android.os.Bundle.class);
            mIndex = method(svc, "j");
            mCount = method(svc, "u");
            mRootView = method(svc, "getRootView");
            return true;
        } catch (Throwable t) {
            LiquidGlassModule.logErr("bili: resolveMethods failed", t);
            return false;
        }
    }

    private static Method method(Class<?> c, String name, Class<?>... params)
            throws NoSuchMethodException {
        Method m;
        try {
            m = c.getDeclaredMethod(name, params);
        } catch (NoSuchMethodException e) {
            m = c.getMethod(name, params);
        }
        // Obfuscated members on the impl class may be package-private.
        m.setAccessible(true);
        return m;
    }

    /**
     * Hide or restore the native bar per the manager's "hide native bar"
     * switch. The real bar is detached on install, so restoring means adding
     * it back at its original index with its original layout params.
     */
    private static void applyNativeBarVisibility() {
        View real = sRealBar.get();
        if (real == null) {
            return;
        }
        if (GlassConfig.biliHideNative) {
            if (real.getParent() instanceof ViewGroup) {
                ((ViewGroup) real.getParent()).removeView(real);
            }
            real.setVisibility(View.GONE);
        } else {
            real.setVisibility(View.VISIBLE);
            ViewGroup p = sRealParent.get();
            if (real.getParent() == null && p != null && sRealLp != null && sRealIdx >= 0) {
                try {
                    p.addView(real, sRealIdx, sRealLp);
                    LiquidGlassModule.log(android.util.Log.INFO,
                            "bili: native bar restored (manager switch)");
                } catch (Throwable t) {
                    LiquidGlassModule.logErr("bili: native bar restore failed", t);
                }
            }
        }
    }

    /**
     * Home re-tap: forward the click to the native bar's own home tab so
     * Bilibili runs its stock re-select refresh (scroll to top + feed reload).
     *
     * The native bar's tab items are usually wrapped: the clickable view is a
     * leaf (or the item view itself), not necessarily child(0) of the bar.
     * A held press must not reach here — the pill consumes long-press.
     */
    private static void refreshHomeFeed(View real) {
        try {
            View tab = firstClickableChild(real);
            if (tab != null) {
                tab.performClick();
                LiquidGlassModule.log(android.util.Log.INFO,
                        "bili: home re-tap -> native click on "
                                + tab.getClass().getName());
                return;
            }
        } catch (Throwable t) {
            LiquidGlassModule.logErr("bili: native refresh failed", t);
        }
        LiquidGlassModule.log(android.util.Log.WARN,
                "bili: no clickable home tab found, falling back to page switch");
        switchTo(0);
    }

    /** Depth-first search for the first clickable view inside the native bar. */
    private static View firstClickableChild(View root) {
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                View hit = firstClickableChild(g.getChildAt(i));
                if (hit != null) {
                    return hit;
                }
            }
        }
        return root.isClickable() ? root : null;
    }

    private static void switchTo(int appIndex) {
        View real = sRealBar.get();
        if (real == null || mSetItem == null) {
            LiquidGlassModule.log(android.util.Log.WARN, "bili: switch ignored, bar absent");
            return;
        }
        try {
            mSetItem.invoke(real, appIndex);
        } catch (Throwable t) {
            long now = SystemClock.uptimeMillis();
            if (now - sLastSwitchFailLog > 5000) {
                sLastSwitchFailLog = now;
                LiquidGlassModule.logErr("bili: setCurrentItem(" + appIndex + ") failed", t);
            }
        }
    }

    private static boolean isNight(Context ctx) {
        return (ctx.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    // ---------------------------------------------------------------- utils

    private static View rootView(Object svc) {
        if (mRootView == null) {
            return null;
        }
        try {
            return (View) mRootView.invoke(svc);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Activity activityOf(Context ctx) {
        for (Context c = ctx; c != null; ) {
            if (c instanceof Activity) {
                return (Activity) c;
            }
            if (c instanceof android.content.ContextWrapper) {
                c = ((android.content.ContextWrapper) c).getBaseContext();
            } else {
                break;
            }
        }
        return null;
    }
}
