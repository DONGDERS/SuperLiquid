package io.github.liuran001.mmliquidglass;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Same click callback invoked by the native tab item, without synthesizing input. */
final class PlayNativeAction {
    private final Field actions;
    private final Field select;
    private final Method invoke;

    PlayNativeAction(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> model = Class.forName("amwa", false, loader);
        Class<?> callbacks = Class.forName("amwe", false, loader);
        Class<?> function = Class.forName("cgik", false, loader);
        actions = model.getField("f");
        select = callbacks.getField("a");
        invoke = function.getMethod("le", Object.class);
        if (actions.getType() != callbacks || select.getType() != function) {
            throw new NoSuchFieldException("Play bottom-bar action ABI mismatch");
        }
    }

    boolean select(Object model, int index) {
        if (index < 0 || index >= 5 || !actions.getDeclaringClass().isInstance(model)) return false;
        try {
            Object callbacks = actions.get(model);
            Object action = callbacks == null ? null : select.get(callbacks);
            if (action == null) return false;
            invoke.invoke(action, Integer.valueOf(index));
            return true;
        } catch (InvocationTargetException e) {
            LiquidGlassModule.logErr("Play native tab selection failed", e.getCause());
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LiquidGlassModule.logErr("Play native tab action unavailable", e);
            return false;
        }
    }
}