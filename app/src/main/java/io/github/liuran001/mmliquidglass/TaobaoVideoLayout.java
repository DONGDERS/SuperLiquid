package io.github.liuran001.mmliquidglass;

import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Preserves the viewport measurement used by Taobao 10.62.20 short video. */
final class TaobaoVideoLayout {
    private static final String MULTI_TAB =
            "com.taobao.android.fluid.flexaremote.framework.container.render.view.MultiTabLayout";
    private static boolean sChecked;

    private TaobaoVideoLayout() { }

    static void onLayout(LiquidGlassHostLayout host, ViewGroup pager) {
        if (sChecked || host.getParent() != pager.getParent()) return;
        View video = findMultiTab(pager);
        if (video == null) return;
        sChecked = true;
        try {
            long version = video.getContext().getPackageManager()
                    .getPackageInfo(HostApp.TAOBAO.pkg, 0).getLongVersionCode();
            if (version != 849) return;
            // Fluid is loaded from a dynamic split. Resolve its helper using the
            // actual view's class loader, even when the split arrives on first entry.
            Class<?> helper = Class.forName("tb.e7f0", false, video.getClass().getClassLoader());
            for (Method method : helper.getDeclaredMethods()) {
                if (!method.getName().equals("a") || method.getReturnType() != int.class
                        || method.getParameterCount() != 0
                        || !Modifier.isStatic(method.getModifiers())) continue;
                LiquidGlassModule.hookIntercept(method, chain -> {
                    Object height = chain.proceed();
                    ViewGroup livePager = LiquidGlassInstaller.currentPager();
                    if (livePager == null || !livePager.isAttachedToWindow()
                            || !(livePager.getParent() instanceof ViewGroup)) {
                        return height;
                    }
                    ViewGroup viewport = (ViewGroup) livePager.getParent();
                    View tabHost = viewport.findViewById(android.R.id.tabhost);
                    if (tabHost == null || !(tabHost.getParent() instanceof LiquidGlassHostLayout)) {
                        return height;
                    }
                    View glassHost = (View) tabHost.getParent();
                    if (glassHost.getParent() == viewport && glassHost.getHeight() > 0
                            && height instanceof Integer && (Integer) height == glassHost.getHeight()
                            && viewport.getHeight() > glassHost.getHeight()) {
                        return viewport.getHeight();
                    }
                    return height;
                });
                // Remeasure after the first layout, which may have used the glass height.
                video.requestLayout();
                LiquidGlassModule.log(android.util.Log.INFO, "Taobao video viewport hook installed");
                return;
            }
            LiquidGlassModule.log(android.util.Log.WARN, "Taobao video viewport helper not found");
        } catch (Throwable t) {
            LiquidGlassModule.logErr("Taobao video viewport hook failed", t);
        }
    }

    private static View findMultiTab(View view) {
        if (view.getVisibility() != View.VISIBLE) return null;
        if (MULTI_TAB.equals(view.getClass().getName())) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findMultiTab(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }
}
