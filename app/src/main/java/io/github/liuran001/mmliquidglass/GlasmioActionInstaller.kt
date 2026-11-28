package io.github.liuran001.mmliquidglass

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout

/** Keep the app's publish callback and replace the entire control in extreme mode. */
internal object GlasmioActionInstaller {
    @JvmStatic
    @JvmOverloads
    fun install(host: LiquidGlassHostLayout, page: ViewGroup, standard: View, iconColor: Int? = null) {
        var ancestor: View? = host
        while (ancestor != null) {
            if (ancestor === page) return
            ancestor = ancestor.parent as? View
        }
        host.setStandardAction(standard)
        var panel: GlasmioPanel? = null
        var failed = false
        var observer: ViewTreeObserver? = null
        fun clear() {
            host.setExtremePanel(null)
            panel?.let(host::removeView)
            panel = null
        }
        fun fail(error: Throwable) {
            if (failed) return
            failed = true
            LiquidGlassModule.logErr("Glasmio publish button failed", error)
            host.post { if (failed) clear() }
            host.invalidate()
        }
        val sync = ViewTreeObserver.OnPreDrawListener {
            if (!GlassConfig.enabled || !GlassConfig.extremePerformance) {
                clear()
                failed = false
            } else if (!failed && host.isShown && page.isShown && host.width > 0 && host.height > 0) {
                try {
                    val button = panel ?: GlasmioPanel(host.context, page, host.isDark(), {}, ::fail,
                        Runnable { standard.performClick() }, iconColor).also {
                        panel = it
                        host.addView(it, FrameLayout.LayoutParams(-1, -1))
                    }
                    button.updateButton(host.isDark())
                    if (!failed && button.isLaidOut && button.width > 0 && button.height > 0) {
                        host.setExtremePanel(button)
                    }
                } catch (error: Throwable) {
                    fail(error)
                }
            }
            true
        }
        val binding = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                observer = host.viewTreeObserver.also { it.addOnPreDrawListener(sync) }
            }
            override fun onViewDetachedFromWindow(view: View) {
                observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(sync)
                observer = null
                host.setExtremePanel(null)
            }
        }
        host.addOnAttachStateChangeListener(binding)
        if (host.isAttachedToWindow) binding.onViewAttachedToWindow(host)
    }
}
