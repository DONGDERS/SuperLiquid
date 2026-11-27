package io.github.liuran001.mmliquidglass

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import java.util.WeakHashMap

/** Experimental Railway material switch. Standard remains available after a failed mount. */
internal class RailwayGlassMode private constructor(
    private val host: LiquidGlassHostLayout,
    private val page: ViewGroup,
    private val standard: LiquidGlassPanel,
) {
    private var extreme: GlasmioPanel? = null
    private var broken = false
    private var mode = false
    private fun fail(error: Throwable) {
        if (broken) return
        broken = true
        LiquidGlassModule.logErr("Glasmio Railway failed; using standard", error)
        host.post {
            extreme?.let { host.removeView(it) }
            extreme = null
            standard.visibility = View.VISIBLE
            standard.invalidate()
        }
    }
    private fun apply() {
        val wanted = GlassConfig.extremePerformance && !broken
        if (!GlassConfig.extremePerformance) broken = false
        if (wanted && extreme == null) {
            try {
                val view = GlasmioPanel(host.context, page, host.isDark(), ::fail)
                extreme = view
                host.addView(view, 1, FrameLayout.LayoutParams(-1, -1))
            } catch (error: Throwable) { fail(error) }
        }
        val enabled = wanted && extreme != null && !broken
        extreme?.visibility = if (enabled) View.VISIBLE else View.GONE
        standard.visibility = if (enabled) View.INVISIBLE else View.VISIBLE
        extreme?.takeIf { enabled }?.refresh(host.isDark())
        if (mode != enabled) {
            mode = enabled
            LiquidGlassModule.log(android.util.Log.INFO,
                "railway material=" + if (enabled) "Glasmio 0.1.4" else "standard")
        }
    }
    companion object {
        private val modes = WeakHashMap<LiquidGlassHostLayout, RailwayGlassMode>()
        @JvmStatic fun attach(host: LiquidGlassHostLayout, page: ViewGroup, standard: LiquidGlassPanel) {
            val bridge = RailwayGlassMode(host, page, standard)
            modes[host] = bridge
            host.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) { bridge.apply() }
                override fun onViewDetachedFromWindow(v: View) { modes.remove(host) }
            })
            bridge.apply()
        }
        @JvmStatic fun refresh(host: LiquidGlassHostLayout) { modes[host]?.apply() }
    }
}