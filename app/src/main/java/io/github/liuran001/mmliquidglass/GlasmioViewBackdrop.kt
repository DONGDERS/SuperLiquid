package io.github.liuran001.mmliquidglass

import android.graphics.Rect
import android.graphics.RecordingCanvas
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import io.github.neoruaa.glasmio.glass.render.Backdrop as GlasmioBackdrop
import kotlin.math.ceil

/** Feeds only the native page into Glasmio; the overlay is never its own source. */
internal class GlasmioViewBackdrop(
    private val page: ViewGroup,
    private val anchor: View,
    private val night: () -> Boolean,
    private val failed: (Throwable) -> Unit,
) : GlasmioBackdrop {
    override val isCoordinatesDependent = true
    private val revision = mutableIntStateOf(0)
    private val position = IntArray(2)
    private val clip = Rect()
    fun refresh() { revision.intValue++ }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        revision.intValue // Subscribe the draw, not the whole composition.
        val canvas = drawContext.canvas.nativeCanvas
        if (canvas !is RecordingCanvas || !page.isShown) return
        val save = canvas.save()
        try {
            ViewGeom.unscaledScreenPos(anchor, position)
            val local = coordinates?.positionInRoot() ?: Offset.Zero
            val scale = ViewGeom.cumulativeScale(anchor)
            if (kotlin.math.abs(scale - 1f) > .001f) {
                canvas.scale(1f / scale, 1f / scale, size.width / 2f, size.height / 2f)
            }
            if (!canvas.getClipBounds(clip) || clip.isEmpty) return
            canvas.translate(clip.left.toFloat(), clip.top.toFloat())
            Backdrop.draw(canvas, page, night(),
                position[0] + local.x.toInt() + clip.left,
                position[1] + local.y.toInt() + clip.top,
                ceil(clip.width().toDouble()).toInt(), ceil(clip.height().toDouble()).toInt())
        } catch (error: Throwable) {
            failed(error)
        } finally {
            canvas.restoreToCount(save)
        }
    }
}
