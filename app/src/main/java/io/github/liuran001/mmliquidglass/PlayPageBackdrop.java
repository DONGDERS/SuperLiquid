package io.github.liuran001.mmliquidglass;

import android.graphics.Canvas;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.os.Looper;
import android.view.View;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;

/** Records only the weighted page slot, before the separate navigation slot. */
final class PlayPageBackdrop {
    private final Method weight;
    private final Method drawWithContent;
    private final Method draw;
    private final Method content;
    private final Method size;
    private final Method context;
    private final Method canvas;
    private final Method setCanvas;
    private final Method ownerOf;
    private final Method coordinator;
    private final Method screenPos;
    private final Field callback;
    private final Field androidCanvas;
    private final Field attached;
    private final Object marker;
    private final Object unit;
    private final Consumer<View> onSourceChanged;
    private boolean pendingSlot;
    private boolean recording;
    private boolean containsBar;
    private boolean disabled;
    private boolean reported;
    private WeakReference<View> owner = new WeakReference<>(null);
    private WeakReference<Object> page = new WeakReference<>(null);
    private RenderNode source;

    PlayPageBackdrop(ClassLoader loader, Consumer<View> onSourceChanged) throws ReflectiveOperationException {
        this.onSourceChanged = onSourceChanged;
        Class<?> action = type(loader, "cgik");
        Class<?> scope = type(loader, "jaj");
        Class<?> modifier = type(loader, "iog");
        Class<?> node = type(loader, "irg");
        Class<?> drawContext = type(loader, "jak");
        Class<?> nodeKind = type(loader, "jsk");
        Class<?> access = type(loader, "jsl");
        weight = type(loader, "byz").getDeclaredMethod("b", modifier, float.class, boolean.class);
        drawWithContent = type(loader, "irc").getMethod("c", modifier, action);
        draw = node.getDeclaredMethod("gR", scope);
        callback = node.getDeclaredField("a");
        callback.setAccessible(true);
        content = scope.getMethod("H");
        size = scope.getMethod("p");
        context = scope.getMethod("q");
        canvas = drawContext.getMethod("b");
        setCanvas = drawContext.getMethod("e", type(loader, "ivw"));
        androidCanvas = type(loader, "iuq").getField("a");
        attached = type(loader, "iof").getField("D");
        ownerOf = access.getMethod("h", nodeKind);
        coordinator = access.getMethod("g", nodeKind, int.class);
        screenPos = type(loader, "jwf").getMethod("k", long.class);
        unit = type(loader, "cgee").getField("a").get(null);
        marker = Proxy.newProxyInstance(action.getClassLoader(), new Class<?>[]{action}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "le": content.invoke(args[0]); return unit;
                case "equals": return proxy == args[0];
                case "hashCode": return System.identityHashCode(proxy);
                case "toString": return "PlayPageDraw";
                default: throw new UnsupportedOperationException(method.getName());
            }
        });
    }

    void install(ClassLoader loader) throws ReflectiveOperationException {
        hookScaffold(type(loader, "vqz"), "g", 6, 7);
        hookScaffold(type(loader, "xmp"), "h", 3, 3);
        LiquidGlassModule.hookIntercept(weight, chain -> {
            if (!main() || !pendingSlot || (float) chain.getArg(1) != 1f
                    || !(boolean) chain.getArg(2)) return chain.proceed();
            pendingSlot = false;
            Object weighted = chain.proceed();
            try {
                return drawWithContent.invoke(null, weighted, marker);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                disableCapture("Play page modifier unavailable", failure);
                return weighted;
            }
        });
        LiquidGlassModule.hookIntercept(draw, chain -> {
            Object node = chain.getThisObject();
            if (!main() || disabled || recording || !GlassConfig.enabled
                    || callback.get(node) != marker) return chain.proceed();
            try {
                if (recordPage(node, chain.getArg(0))) return null;
            } catch (ReflectiveOperationException | RuntimeException failure) {
                disableCapture("Play page recording unavailable", failure);
            }
            return chain.proceed();
        });
    }

    private void hookScaffold(Class<?> type, String discriminator, int first, int second)
            throws ReflectiveOperationException {
        Field branch = type.getDeclaredField(discriminator);
        branch.setAccessible(true);
        Method compose = type.getDeclaredMethod("a", Object.class, Object.class, Object.class, Object.class);
        LiquidGlassModule.hookIntercept(compose, chain -> {
            if (!main() || disabled) return chain.proceed();
            int id = branch.getInt(chain.getThisObject());
            if (id != first && id != second) return chain.proceed();
            boolean previous = pendingSlot;
            pendingSlot = true;
            try {
                return chain.proceed();
            } finally {
                pendingSlot = previous;
            }
        });
    }

    private boolean recordPage(Object node, Object scope) throws Throwable {
        if (!attached.getBoolean(node) || !(ownerOf.invoke(null, node) instanceof View view)
                || !view.isAttachedToWindow() || !view.isShown()) return false;
        Object ctx = context.invoke(scope);
        Object wrapped = canvas.invoke(ctx);
        if (!androidCanvas.getDeclaringClass().isInstance(wrapped)
                || !(androidCanvas.get(wrapped) instanceof Canvas original)
                || !original.isHardwareAccelerated()) return false;
        long packed = (long) size.invoke(scope);
        int width = Math.round(high(packed));
        int height = Math.round(low(packed));
        if (width < view.getWidth() * .8f || height < view.getHeight() * .35f
                || width > 4096 || height > 4096 || width <= 0 || height <= 0) return false;
        Object coordinates = coordinator.invoke(null, node, 4);
        long location = (long) screenPos.invoke(coordinates, 0L);
        float x = high(location), y = low(location);
        if (!Float.isFinite(x) || !Float.isFinite(y)) return false;

        boolean changed = owner.get() != view || page.get() != node || source == null;
        RenderNode layer = changed ? new RenderNode("Play page only") : source;
        // Compose uses a separate wrapper when drawing content into a recording canvas.
        Object recorder = androidCanvas.getDeclaringClass().getDeclaredConstructor().newInstance();
        layer.setPosition(0, 0, width, height);
        layer.setClipToBounds(false);
        RecordingCanvas target = layer.beginRecording(width, height);
        containsBar = false;
        recording = true;
        try {
            androidCanvas.set(recorder, target);
            setCanvas.invoke(ctx, recorder);
            content.invoke(scope);
        } finally {
            recording = false;
            try {
                setCanvas.invoke(ctx, wrapped);
            } finally {
                layer.endRecording();
            }
        }
        original.drawRenderNode(layer);
        if (containsBar) {
            disabled = true;
            clear();
            view.post(() -> onSourceChanged.accept(view));
            LiquidGlassModule.log(android.util.Log.WARN, "Play page capture rejected: contains navigation");
            return true;
        }
        if (changed) {
            owner = new WeakReference<>(view);
            page = new WeakReference<>(node);
            source = layer;
            view.post(() -> onSourceChanged.accept(view));
        }
        return true;
    }

    LiquidGlassPanel.EmbeddedBackdrop forBar(View view) {
        if (recording) {
            containsBar = true;
            return null;
        }
        if (disabled || owner.get() != view || source == null) return null;
        Object node = page.get();
        if (node == null) return null;
        try {
            if (!attached.getBoolean(node) || ownerOf.invoke(null, node) != view) return null;
            Object coordinates = coordinator.invoke(null, node, 4);
            long at = (long) screenPos.invoke(coordinates, 0L);
            float x = high(at), y = low(at);
            if (!Float.isFinite(x) || !Float.isFinite(y)) return null;
            RenderNode layer = source;
            return (target, left, top, width, height, pad) -> {
                // Blur padding alone does not make the glass overlap the page.
                int pageWidth = layer.getWidth(), pageHeight = layer.getHeight();
                if (!layer.hasDisplayList() || x >= left + width - pad || y >= top + height - pad
                        || x + pageWidth <= left + pad || y + pageHeight <= top + pad) return false;
                int save = target.save();
                try {
                    target.translate(x - left, y - top);
                    target.clipRect(0, 0, pageWidth, pageHeight);
                    target.drawRenderNode(layer);
                } finally {
                    target.restoreToCount(save);
                }
                if (!reported) {
                    reported = true;
                    LiquidGlassModule.log(android.util.Log.INFO, "Play glass captured page backdrop");
                }
                return true;
            };
        } catch (ReflectiveOperationException | RuntimeException failure) {
            clear();
            LiquidGlassModule.logErr("Play page backdrop unavailable", failure);
            return null;
        }
    }

    private void disableCapture(String message, Throwable failure) {
        disabled = true;
        View previous = owner.get();
        clear();
        if (previous != null) previous.post(() -> onSourceChanged.accept(previous));
        LiquidGlassModule.logErr(message, failure);
    }

    void clear() {
        owner.clear();
        page.clear();
        source = null;
        reported = false;
    }

    private static boolean main() { return Looper.myLooper() == Looper.getMainLooper(); }
    private static float high(long value) { return Float.intBitsToFloat((int) (value >>> 32)); }
    private static float low(long value) { return Float.intBitsToFloat((int) value); }
    private static Class<?> type(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }
}
