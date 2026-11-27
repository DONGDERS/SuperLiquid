package io.github.liuran001.mmliquidglass

import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.neoruaa.glasmio.ui.GlasmioTabItem
import io.github.neoruaa.glasmio.ui.NavigationBarSurfaceHeight
import kotlin.math.roundToInt

/** A sibling of the standard bar, with no standard views in its render or input path. */
internal class GlasmioNavigationInstaller(
    private val container: FrameLayout,
    private val page: ViewGroup,
    private val onSelected: java.util.function.IntConsumer,
) {
    private var panel: GlasmioPanel? = null
    private var failed = false

    fun show(
        requested: Boolean,
        active: Boolean,
        items: List<GlasmioTabItem<Int>>,
        selected: Int,
        night: Boolean,
        bottom: Int,
        offsetX: Int,
        widthScale: Float,
        scale: Float,
    ): Boolean {
        if (!requested || failed) {
            close()
            if (!requested) failed = false
            return false
        }
        if (!active || items.isEmpty() || container.width <= 0) {
            panel?.visibility = View.INVISIBLE
            return false
        }
        val density = container.resources.displayMetrics.density
        val maxWidth = (container.width - (24 * density).roundToInt()).coerceAtLeast(1)
        val width = ((82 * items.size + 8) * density * widthScale).roundToInt().coerceIn(1, maxWidth)
        val height = (NavigationBarSurfaceHeight.value * density).roundToInt().coerceAtLeast(1)
        try {
            val view = panel ?: GlasmioPanel(container.context, page, night, onSelected::accept, ::fail)
                .also {
                    panel = it
                    it.update(items, selected, night)
                    container.addView(it, FrameLayout.LayoutParams(width, height, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                        .apply { bottomMargin = bottom })
                    if (!failed) LiquidGlassModule.log(Log.INFO, "Glasmio navigation installed")
                }
            if (failed) return false
            val lp = view.layoutParams as FrameLayout.LayoutParams
            if (lp.width != width || lp.height != height || lp.bottomMargin != bottom) {
                lp.width = width
                lp.height = height
                lp.bottomMargin = bottom
                view.layoutParams = lp
            }
            view.pivotX = width / 2f
            view.pivotY = height.toFloat()
            view.translationX = offsetX.toFloat()
            view.scaleX = scale
            view.scaleY = scale
            view.update(items, selected, night)
            view.visibility = View.VISIBLE
            return true
        } catch (error: Throwable) {
            fail(error)
            return false
        }
    }

    private fun fail(error: Throwable) {
        if (failed) return
        failed = true
        LiquidGlassModule.logErr("Glasmio navigation failed; using full standard bar", error)
        container.post { if (failed) close() }
        container.invalidate()
    }

    fun close() {
        panel?.let(container::removeView)
        panel = null
    }
}
