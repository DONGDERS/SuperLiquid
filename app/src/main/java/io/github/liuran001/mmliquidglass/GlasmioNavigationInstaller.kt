package io.github.liuran001.mmliquidglass

import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.liuran001.mmliquidglass.glasmionav.GlasmioTabItem
import io.github.liuran001.mmliquidglass.glasmionav.NavigationBarSurfaceHeight
import io.github.neoruaa.glasmio.icons.SfSymbol
import kotlin.math.roundToInt

/** Uses the same tab cells as the standard installer, without moving them. */
internal class GlasmioNavigationInstaller(
    private val container: FrameLayout,
    private val page: ViewGroup,
    private var row: ViewGroup,
) {
    private var panel: GlasmioPanel? = null
    private var failed = false
    private var items = emptyList<GlasmioTabItem<View>>()

    fun hasFailed(): Boolean = failed

    fun activePanel(): View? = panel?.takeIf { !failed && it.visibility == View.VISIBLE && it.isLaidOut && it.width > 0 && it.height > 0 }

    fun bindRow(next: ViewGroup) { row = next }

    @JvmOverloads
    fun show(
        requested: Boolean,
        active: Boolean,
        night: Boolean,
        bottom: Int,
        offsetX: Int,
        widthScale: Float,
        scale: Float,
        selection: Int = TabBarBridge.selectedIndex(row),
        fitHost: Boolean = false,
    ): Boolean {
        if (!requested || failed) {
            close()
            if (!requested) failed = false
            return false
        }
        if (!active || container.width <= 0) {
            panel?.visibility = View.INVISIBLE
            return false
        }
        val cells = (0 until TabBarBridge.tabCount(row)).mapNotNull { TabBarBridge.tabAt(row, it) }
        if (cells.isEmpty() || cells.any { it.width == 0 || it.height == 0 }) {
            panel?.visibility = View.INVISIBLE
            return false
        }
        if (cells.size != items.size || cells.indices.any { cells[it] !== items[it].key }) {
            items = cells.map { cell ->
                val label = cell.contentDescription?.toString()?.takeIf { it.isNotBlank() }
                    ?: RuntimeTabCatalog.label(cell)
                GlasmioTabItem(cell, label, SfSymbol.House, label)
            }
        }
        val selected = if (selection >= 0) TabBarBridge.tabAt(row, selection) else null
        val accent = selected?.let { RuntimeTabCatalog.titleView(it)?.currentTextColor }
        val content = cells.firstOrNull { it !== selected }?.let { RuntimeTabCatalog.titleView(it)?.currentTextColor }

        val density = container.resources.displayMetrics.density
        val reserve = if (fitHost) container.paddingLeft + container.paddingRight
            else (24 * density).roundToInt()
        val maxWidth = (container.width - reserve).coerceAtLeast(1)
        val width = if (fitHost) maxWidth else
            ((82 * items.size + 8) * density * widthScale).roundToInt().coerceIn(1, maxWidth)
        val height = if (fitHost && LiquidGlassModule.app() == HostApp.QQ && row.height > 0) row.height
            else (NavigationBarSurfaceHeight.value * density).roundToInt().coerceAtLeast(1)
        try {
            val view = panel ?: GlasmioPanel(container.context, page, night, { cell ->
                if (cell.parent === row && cell.visibility == View.VISIBLE && cell.isEnabled) {
                    cell.performClick()
                    LiquidGlassInstaller.requestSelectionSync()
                }
            }, ::fail)
                .also {
                    panel = it
                    it.updateSurfaceHeight(height)
                    it.update(items, selected, night, accent, content)
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
            view.updateSurfaceHeight(height)
            view.update(items, selected, night, accent, content)
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
