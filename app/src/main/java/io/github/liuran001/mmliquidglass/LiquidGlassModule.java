package io.github.liuran001.mmliquidglass;

import android.app.Activity;
import android.app.Instrumentation;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

public class LiquidGlassModule extends XposedModule {

    static final String TAG = "LiquidGlass";

    /** The app this process belongs to; null until onModuleLoaded resolves it. */
    private static volatile HostApp sApp;

    private static volatile int sResumeHits;
    private static volatile LiquidGlassModule sSelf;

    public LiquidGlassModule() {
        super();
        sSelf = this;
    }

    static HostApp app() {
        return sApp;
    }

    /** Hooks an executable, running fn AFTER the original and ignoring its result. */
    static void hookAfter(java.lang.reflect.Executable ex, AfterCallback fn) {
        LiquidGlassModule self = sSelf;
        if (self == null) {
            throw new IllegalStateException("module instance not attached yet");
        }
        self.hook(ex)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    try {
                        fn.after(chain);
                    } catch (Throwable t) {
                        logErr("after-hook failed", t);
                    }
                    return result;
                });
    }

    /**
     * Hooks an executable, handing the callback the whole call chain so it can
     * rewrite arguments, substitute a result, or drop the call outright.
     *
     * <p>PROTECTIVE like {@link #hookAfter}: a throw out of the callback must
     * never surface in the host's own frame. The chain here wraps app calls
     * that legitimately throw — ViewPager2 rejects setCurrentItem mid
     * fake-drag — and reflection would repackage that as an
     * InvocationTargetException the app has no catch for.
     */
    static void hookIntercept(java.lang.reflect.Executable ex, InterceptCallback fn) {
        LiquidGlassModule self = sSelf;
        if (self == null) {
            throw new IllegalStateException("module instance not attached yet");
        }
        self.hook(ex)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(fn::intercept);
    }

    interface InterceptCallback {
        Object intercept(XposedInterface.Chain chain) throws Throwable;
    }

    interface AfterCallback {
        void after(XposedInterface.Chain chain) throws Throwable;
    }

    public static final String BUILD = "0.3.9-f10";

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        String proc = param.getProcessName();
        log(android.util.Log.INFO, "onModuleLoaded BUILD=" + BUILD + " process=" + proc
                + " api=" + getApiVersion()
                + " framework=" + getFrameworkName() + " " + getFrameworkVersion());
        // Both targets are heavily multi-process (:push, :tools, :MSF,
        // :appbrandX, ...). The home screen lives in the main process only,
        // whose name is the package name; everything else detaches.
        HostApp app = HostApp.forProcess(proc);
        if (app == null) {
            log(android.util.Log.INFO, "not a main process we dress up, detach");
            detach();
            return;
        }
        sApp = app;
        try {
            Method callOnResume = Instrumentation.class.getMethod(
                    "callActivityOnResume", Activity.class);
            hook(callOnResume)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            Object arg0 = chain.getArg(0);
                            if (arg0 instanceof Activity) {
                                Activity activity = (Activity) arg0;
                                String name = arg0.getClass().getName();
                                if (app.launcherActivity.equals(name)) {
                                    GlassConfig.load(activity);
                                    sResumeHits++;
                                    if (sResumeHits <= 3 || sResumeHits % 20 == 0) {
                                        log(android.util.Log.INFO,
                                                "home activity onResume #" + sResumeHits);
                                    }
                                    LiquidGlassInstaller.scheduleInstall(activity);
                                    if (app == HostApp.BILIBILI) {
                                        // The service may only be reachable
                                        // through the live view tree (Tinker
                                        // loader); hand the hook the activity.
                                        BiliHomeBar.onHomeResume(activity);
                                    }
                                }
                            }
                        } catch (Throwable t) {
                            logErr("resume hook error", t);
                        }
                        return result;
                    });
            log(android.util.Log.INFO, "hooked Instrumentation.callActivityOnResume for "
                    + app + " (" + app.launcherActivity + ")");
        } catch (Throwable t) {
            logErr("install resume hook failed", t);
        }
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        HostApp app = HostApp.forPackage(param.getPackageName());
        // Subprocesses detach in onModuleLoaded; without this gate the package
        // hook fires there too (:ijkservice ran our service hook once), and a
        // second LiquidGlass instance would watch routers it must never touch.
        if (app == null || sApp == null || !param.isFirstPackage()) {
            return;
        }
        sApp = app;
        log(android.util.Log.INFO, "target package loaded: " + app
                + " classLoader=" + param.getDefaultClassLoader());
        // Manager-side config (remote prefs → XSharedPreferences), keyed to
        // this app. Must run before any install path consumes the values.
        GlassConfig.setModule(this, param.getPackageName());
        if (!GlassConfig.enabled) {
            log(android.util.Log.INFO,
                    "manager config: disabled for " + app + ", skipping install");
            return;
        }
        // The tab bar bridge needs the app's own classes, so it can only be
        // wired once the app class loader exists.
        if (app == HostApp.BILIBILI) {
            // Compose bar: the service bridge does its own hooking and later
            // feeds a self-built row through the ordinary installer entry.
            BiliHomeBar.install(app, param.getDefaultClassLoader());
        } else {
            TabBarBridge.install(app, param.getDefaultClassLoader());
        }
    }

    static void log(int prio, String msg) {
        android.util.Log.println(prio, TAG, msg);
    }

    static void logErr(String msg, Throwable t) {
        android.util.Log.e(TAG, msg, t);
    }
}
