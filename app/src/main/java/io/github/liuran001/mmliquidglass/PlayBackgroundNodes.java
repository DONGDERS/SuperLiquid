package io.github.liuran001.mmliquidglass;

import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;

import io.github.libxposed.api.XposedInterface;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

/** Carries bottom-bar identity from Compose modifier creation to actual drawing. */
final class PlayBackgroundNodes {
    enum Layer { RECTANGULAR_FILL, SURFACE }

    interface Surface {
        /** Return false to keep the native fill. Coordinates are local to the draw scope. */
        boolean draw(View owner, Canvas canvas, Layer layer, float x, float y,
                     float width, float height);
    }

    private static final class Redraw {
        final WeakReference<Object> node;
        final WeakReference<View> owner;

        Redraw(Object node, View owner) {
            this.node = new WeakReference<>(node);
            this.owner = new WeakReference<>(owner);
        }
    }

    private static final class Marks {
        private final ArrayList<WeakReference<Object>> keys = new ArrayList<>();
        private final ArrayList<Layer> values = new ArrayList<>();

        Layer get(Object key) {
            Layer found = null;
            for (int i = keys.size() - 1; i >= 0; i--) {
                Object existing = keys.get(i).get();
                if (existing == null) {
                    keys.remove(i);
                    values.remove(i);
                } else if (existing == key) {
                    found = values.get(i);
                }
            }
            return found;
        }

        void set(Object key, Layer layer) {
            if (key == null) return;
            for (int i = keys.size() - 1; i >= 0; i--) {
                Object existing = keys.get(i).get();
                if (existing == null || existing == key) {
                    keys.remove(i);
                    values.remove(i);
                }
            }
            if (layer != null) {
                keys.add(new WeakReference<>(key));
                values.add(layer);
            }
        }
    }

    private final Marks modifiers = new Marks();
    private final Marks nodes = new Marks();
    private final Method newNode;
    private final Method updateNode;
    private final Method drawNode;
    private final Method drawContent;
    private final Method drawSize;
    private final Method drawContext;
    private final Method composeCanvas;
    private final Field nativeCanvas;
    private final Method nodeOwner;
    private final Method nodeCoordinator;
    private final Method localToScreen;
    private final Method invalidateDraw;
    private final Field attached;
    private final ArrayList<Redraw> redraws = new ArrayList<>();
    private Layer creating;
    private Surface surface;

    PlayBackgroundNodes(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> modifier = type(loader, "bcm");
        Class<?> node = type(loader, "bcq");
        Class<?> scope = type(loader, "jaj");
        newNode = modifier.getDeclaredMethod("d");
        updateNode = modifier.getDeclaredMethod("f", type(loader, "iof"));
        drawNode = node.getDeclaredMethod("gR", scope);
        drawContent = scope.getMethod("H");
        drawSize = scope.getMethod("p");
        drawContext = scope.getMethod("q");
        composeCanvas = type(loader, "jak").getMethod("b");
        nativeCanvas = type(loader, "iuq").getField("a");
        Class<?> nodeAccess = type(loader, "jsl");
        Class<?> nodeKind = type(loader, "jsk");
        nodeOwner = nodeAccess.getMethod("h", nodeKind);
        nodeCoordinator = nodeAccess.getMethod("g", nodeKind, int.class);
        localToScreen = type(loader, "jwf").getMethod("k", long.class);
        invalidateDraw = type(loader, "jsw").getMethod("a", type(loader, "jsv"));
        attached = type(loader, "iof").getField("D");
        if (nativeCanvas.getType() != Canvas.class || drawSize.getReturnType() != long.class) {
            throw new NoSuchMethodException("Play draw scope ABI mismatch");
        }
    }

    void install(ClassLoader loader) throws ReflectiveOperationException {
        // Resolve before installing: no partial ABI guesses. No fill is skipped
        // until a renderer explicitly claims it during a draw.
        var construct = type(loader, "bcm").getDeclaredConstructor(long.class,
                type(loader, "ivs"), float.class, type(loader, "iye"), int.class);
        LiquidGlassModule.hookAfter(construct, chain -> {
            if (mainThread() && creating != null && (int) chain.getArg(4) == 2) {
                modifiers.set(chain.getThisObject(), creating);
            }
        });
        LiquidGlassModule.hookIntercept(newNode, chain -> {
            Object node = chain.proceed();
            if (mainThread()) nodes.set(node, modifiers.get(chain.getThisObject()));
            return node;
        });
        LiquidGlassModule.hookAfter(updateNode, chain -> {
            if (mainThread()) nodes.set(chain.getArg(0), modifiers.get(chain.getThisObject()));
        });
        LiquidGlassModule.hookIntercept(drawNode, chain -> {
            if (!mainThread() || surface == null) return chain.proceed();
            Layer layer = nodes.get(chain.getThisObject());
            if (layer == null || !drawReplacement(chain.getThisObject(), chain.getArg(0), layer)) return chain.proceed();
            // bcq.gR ends with exactly this drawContent call. Keep it once,
            // outside the replacement's canvas save scope, with native colors.
            return drawContent.invoke(chain.getArg(0));
        });
    }

    Object markNextFill(Layer layer, XposedInterface.Chain chain) throws Throwable {
        if (!mainThread()) return chain.proceed();
        Layer previous = creating;
        creating = layer;
        try {
            return chain.proceed();
        } finally {
            creating = previous;
        }
    }

    boolean isEnabled() { return surface != null; }

    void setSurface(Surface value) {
        surface = value;
        invalidate(null);
    }

    void invalidate(View owner) {
        for (int i = redraws.size() - 1; i >= 0; i--) {
            Redraw entry = redraws.get(i);
            Object node = entry.node.get();
            View view = entry.owner.get();
            if (node == null || view == null || !view.isAttachedToWindow()) {
                redraws.remove(i);
                continue;
            }
            if (owner != null && owner != view) continue;
            try {
                if (attached.getBoolean(node)) invalidateDraw.invoke(null, node);
                else redraws.remove(i);
            } catch (ReflectiveOperationException ex) {
                redraws.remove(i);
                view.invalidate();
            }
        }
    }

    private void rememberDraw(Object node, View owner) {
        for (int i = redraws.size() - 1; i >= 0; i--) {
            Object old = redraws.get(i).node.get();
            if (old == node) return;
            if (old == null) redraws.remove(i);
        }
        redraws.add(new Redraw(node, owner));
    }

    private boolean drawReplacement(Object node, Object scope, Layer layer) {
        try {
            if (!attached.getBoolean(node)) return false;
            Object host = nodeOwner.invoke(null, node);
            if (!(host instanceof View)) return false;
            View owner = (View) host;
            if (!owner.isShown() || !owner.isAttachedToWindow()) return false;
            Object coords = nodeCoordinator.invoke(null, node, 1);
            long origin = (long) localToScreen.invoke(coords, 0L);
            float x = Float.intBitsToFloat((int) (origin >>> 32));
            float y = Float.intBitsToFloat((int) origin);
            if (!Float.isFinite(x) || !Float.isFinite(y)) return false;
            rememberDraw(node, owner);
            Object wrapped = composeCanvas.invoke(drawContext.invoke(scope));
            if (!nativeCanvas.getDeclaringClass().isInstance(wrapped)) return false;
            Canvas canvas = (Canvas) nativeCanvas.get(wrapped);
            if (canvas == null) return false;
            long size = (long) drawSize.invoke(scope);
            float width = Float.intBitsToFloat((int) (size >>> 32));
            float height = Float.intBitsToFloat((int) size);
            if (!(width > 0f && height > 0f) || !Float.isFinite(width) || !Float.isFinite(height)) {
                return false;
            }
            int save = canvas.save();
            try {
                return surface.draw(owner, canvas, layer, x, y, width, height);
            } finally {
                canvas.restoreToCount(save);
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            surface = null;
            LiquidGlassModule.logErr("Play bottom-bar background; restoring native fill", failure);
            return false;
        }
    }

    private static boolean mainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    private static Class<?> type(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }
}
