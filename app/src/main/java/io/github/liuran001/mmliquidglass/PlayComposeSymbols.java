package io.github.liuran001.mmliquidglass;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/** Exact bottom-bar ABI for Play Store 52.9.21-24. */
final class PlayComposeSymbols {
    static final long VERSION_CODE = 85292100L;

    final Method legacyBar;
    final Method materialBar;
    private final Field tabs;
    private final Field selected;

    PlayComposeSymbols(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> owner = type(loader, "atis");
        Class<?> model = type(loader, "amwa");
        Class<?> callback = type(loader, "ceqm");
        Class<?> modifier = type(loader, "iog");
        Class<?> style = type(loader, "amwy");
        Class<?> composer = type(loader, "hue");
        legacyBar = owner.getDeclaredMethod("ff", model, callback, callback, callback,
                modifier, style, composer, int.class);
        materialBar = owner.getDeclaredMethod("fg", model, callback, callback, callback,
                modifier, int.class, style, composer, int.class);
        tabs = model.getField("a");
        selected = model.getField("b");
        if (!isComposable(legacyBar) || !isComposable(materialBar)
                || tabs.getType() != List.class || selected.getType() != int.class
                || Modifier.isStatic(tabs.getModifiers())
                || Modifier.isStatic(selected.getModifiers())) {
            throw new NoSuchMethodException("Play bottom-bar ABI mismatch");
        }
    }

    int selectedIndex(Object model, int visibleTabCount) throws IllegalAccessException {
        if (visibleTabCount <= 0 || !tabs.getDeclaringClass().isInstance(model)) {
            return PlayTabSelection.UNKNOWN;
        }
        List<?> items = (List<?>) tabs.get(model);
        if (items == null || items.size() != visibleTabCount) return PlayTabSelection.UNKNOWN;
        int index = selected.getInt(model);
        return index >= 0 && index < visibleTabCount ? index : PlayTabSelection.UNKNOWN;
    }

    private static Class<?> type(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }

    private static boolean isComposable(Method method) {
        return Modifier.isStatic(method.getModifiers()) && method.getReturnType() == void.class;
    }
}
