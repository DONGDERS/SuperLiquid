package io.github.liuran001.mmliquidglass;

import android.app.Activity;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import io.github.libxposed.api.XposedInterface;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;

/** Tracks the exact Play bottom-bar composition, including independently restarted scopes. */
final class PlayHomeBar {
    interface NativeSelection {
        void onSelection(int index);
    }

    private final PlayComposeSymbols symbols;
    private final PlayNativeAction nativeAction;
    private final Method touchDispatch;
    private PlayBarBinding binding;
    private Object currentModel;
    private final PlayBackgroundNodes backgrounds;
    private final Method legacySurface;
    private final Method materialFill;
    private final Method materialSurface;
    private final CallbackScope legacyRestart;
    private final CallbackScope materialRestart;
    private final CallbackScope materialImplementationRestart;
    private final CallbackScope surfaceDraw;
    private int currentSelection = PlayTabSelection.UNKNOWN;
    private NativeSelection selection;
    private boolean ready;
    private boolean legacy;
    private boolean material;
    private boolean insideSurface;

    PlayHomeBar(ClassLoader loader) throws ReflectiveOperationException {
        symbols = new PlayComposeSymbols(loader);
        nativeAction = new PlayNativeAction(loader);
        touchDispatch = type(loader, "jzz").getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
        backgrounds = new PlayBackgroundNodes(loader);
        Class<?> owner = type(loader, "atis");
        Class<?> modifier = type(loader, "iog");
        Class<?> composer = type(loader, "hue");
        Class<?> content = type(loader, "cgio");
        Class<?> shape = type(loader, "iye");
        Class<?> border = type(loader, "bdd");
        Class<?> colors = type(loader, "cel");
        legacySurface = owner.getDeclaredMethod("hq", owner, modifier,
                type(loader, "cdb"), type(loader, "cgip"), composer, int.class);
        materialFill = type(loader, "bcn").getDeclaredMethod("c", modifier, long.class);
        materialSurface = type(loader, "bmrx").getDeclaredMethod("cT", modifier,
                long.class, long.class, colors, int.class, content, composer, int.class);
        legacyRestart = new CallbackScope(type(loader, "akif"), owner, modifier,
                type(loader, "cdb"), type(loader, "cgip"), int.class, int.class);
        materialRestart = new CallbackScope(type(loader, "fss"), modifier, long.class,
                long.class, colors, int.class, content, int.class);
        materialImplementationRestart = new CallbackScope(type(loader, "ffl"),
                type(loader, "ffm"), type(loader, "fsx"), int.class);
        surfaceDraw = new CallbackScope(type(loader, "fvo"), modifier, shape, long.class,
                float.class, border, float.class, content);
    }

    void install(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> modifier = type(loader, "iog");
        Method makeSurface = type(loader, "fvt").getDeclaredMethod("a", modifier,
                type(loader, "iye"), long.class, type(loader, "bdd"), float.class);
        backgrounds.install(loader);
        LiquidGlassModule.hookIntercept(symbols.legacyBar, c -> composeBar(c, true));
        LiquidGlassModule.hookIntercept(symbols.materialBar, c -> composeBar(c, false));
        LiquidGlassModule.hookIntercept(legacySurface, c -> {
            if (!ready || !mainThread() || !legacy) return c.proceed();
            return composeSurface(c);
        });
        LiquidGlassModule.hookIntercept(materialSurface, c -> {
            if (!ready || !mainThread() || !material) return c.proceed();
            return composeSurface(c);
        });
        LiquidGlassModule.hookIntercept(materialFill, c -> {
            if (!ready || !mainThread() || !material || insideSurface) return c.proceed();
            return backgrounds.markNextFill(PlayBackgroundNodes.Layer.RECTANGULAR_FILL, c);
        });
        LiquidGlassModule.hookIntercept(makeSurface, c -> {
            if (!ready || !mainThread() || !surfaceDraw.running) return c.proceed();
            return backgrounds.markNextFill(PlayBackgroundNodes.Layer.SURFACE, c);
        });
        legacyRestart.install(() -> ready && legacy && insideSurface, this::resumeLegacy);
        materialRestart.install(() -> ready && insideSurface, this::composeSurface);
        materialImplementationRestart.install(() -> ready && insideSurface, this::composeSurface);
        surfaceDraw.install(() -> ready && insideSurface, XposedInterface.Chain::proceed);
        LiquidGlassModule.hookIntercept(touchDispatch, c -> {
            PlayBarBinding live = binding;
            if (!ready || !GlassConfig.enabled || !mainThread() || !backgrounds.isEnabled() || live == null) return c.proceed();
            return live.dispatch((View) c.getThisObject(), c.getArg(0),
                    event -> event == c.getArg(0) ? c.proceed() : c.proceed(new Object[]{event}));
        });
        ready = true;
    }

    /** Set only from a version-verified, attached home binding on the UI thread. */
    void resume(Activity activity) {
        View decor = activity.getWindow().getDecorView();
        if (!ready || !GlassConfig.enabled) {
            if (binding != null) binding.close();
            return;
        }
        if (binding != null && binding.owns(decor)) return;
        if (binding != null) binding.close();
        binding = new PlayBarBinding(activity, this);
    }

    void unbind(PlayBarBinding value) {
        if (binding != value) return;
        binding = null;
        currentModel = null;
        currentSelection = PlayTabSelection.UNKNOWN;
        bind(null, null);
    }

    boolean select(int index) {
        return nativeAction.select(currentModel, index);
    }

    void bind(NativeSelection listener, PlayBackgroundNodes.Surface renderer) {
        selection = listener;
        backgrounds.setSurface(listener == null ? null : renderer);
        if (listener != null && currentSelection != PlayTabSelection.UNKNOWN) {
            listener.onSelection(currentSelection);
        }
    }

    void invalidate(View owner) {
        backgrounds.invalidate(owner);
    }

    private Object composeBar(XposedInterface.Chain chain, boolean useLegacy) throws Throwable {
        if (!ready || !mainThread()) return chain.proceed();
        int selected = symbols.selectedIndex(chain.getArg(0), 5);
        if (selected == PlayTabSelection.UNKNOWN) return chain.proceed();
        boolean previousLegacy = legacy;
        boolean previousMaterial = material;
        legacy = useLegacy;
        material = !useLegacy;
        try {
            currentSelection = selected;
            currentModel = chain.getArg(0);
            if (selection != null) selection.onSelection(selected);
            return chain.proceed();
        } finally {
            legacy = previousLegacy;
            material = previousMaterial;
        }
    }

    private Object resumeLegacy(XposedInterface.Chain chain) throws Throwable {
        boolean previous = legacy;
        legacy = true;
        try {
            return chain.proceed();
        } finally {
            legacy = previous;
        }
    }

    private Object composeSurface(XposedInterface.Chain chain) throws Throwable {
        boolean previous = insideSurface;
        insideSurface = true;
        try {
            return chain.proceed();
        } finally {
            insideSurface = previous;
        }
    }

    private interface Active { boolean get(); }

    private static final class CallbackScope {
        private final ArrayList<WeakReference<Object>> marked = new ArrayList<>();
        private final Constructor<?> constructor;
        private final Method invoke;
        boolean running;

        CallbackScope(Class<?> owner, Class<?>... params) throws ReflectiveOperationException {
            constructor = owner.getDeclaredConstructor(params);
            invoke = owner.getDeclaredMethod("a", Object.class, Object.class);
        }

        void install(Active active, LiquidGlassModule.InterceptCallback callback) {
            LiquidGlassModule.hookAfter(constructor, c -> {
                if (mainThread() && active.get()) {
                    contains(null);
                    marked.add(new WeakReference<>(c.getThisObject()));
                }
            });
            LiquidGlassModule.hookIntercept(invoke, c -> {
                if (!mainThread()) return c.proceed();
                boolean previous = running;
                running = contains(c.getThisObject());
                try {
                    return running ? callback.intercept(c) : c.proceed();
                } finally {
                    running = previous;
                }
            });
        }

        private boolean contains(Object target) {
            boolean found = false;
            for (int i = marked.size() - 1; i >= 0; i--) {
                Object candidate = marked.get(i).get();
                if (candidate == null) marked.remove(i);
                else if (candidate == target) found = true;
            }
            return found;
        }
    }

    private static boolean mainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    private static Class<?> type(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }
}