package io.github.liuran001.mmliquidglass

import android.content.ComponentCallbacks
import android.content.Context
import android.content.ContextWrapper

import android.graphics.Canvas
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.Alignment
import io.github.neoruaa.glasmio.ui.GlasmioIconButton
import io.github.neoruaa.glasmio.icons.SfIcon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ComposeViewContext
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer

import android.widget.FrameLayout
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.liuran001.mmliquidglass.glasmionav.GlasmioNavigationBar
import io.github.liuran001.mmliquidglass.glasmionav.GlasmioTabItem
import io.github.liuran001.mmliquidglass.glasmionav.LocalNavigationViewContent
import io.github.neoruaa.glasmio.core.defaultGlassColors
import io.github.neoruaa.glasmio.core.glasmioColorScheme
import android.view.View
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp

/** The complete Glasmio bottom navigation, including its own input handling. */
internal class GlasmioPanel(
    context: Context, private val page: ViewGroup, night: Boolean,
    private val onSelected: (View) -> Unit,
    private val failed: (Throwable) -> Unit,
    private val onAdd: Runnable? = null,
) : FrameLayout(context) {
    private var dark by mutableStateOf(night)
    private var blur by mutableStateOf(configuredBlur())
    private var surfaceHeightPx by androidx.compose.runtime.mutableIntStateOf(0)
    private fun configuredBlur() = (5f * GlassConfig.blurRadius.coerceIn(1, 8) / 3f).dp
    fun updateButton(night: Boolean) { dark = night }
    fun updateSurfaceHeight(px: Int) { surfaceHeightPx = px }
    private var items by mutableStateOf(emptyList<GlasmioTabItem<View>>())
    private var selected by mutableStateOf<View?>(null)
    private var accent by mutableStateOf<Int?>(null)
    private var contentColor by mutableStateOf<Int?>(null)
    private val contentRevision = androidx.compose.runtime.mutableIntStateOf(0)

    private fun refreshTabContent() { contentRevision.intValue++ }
    private var owner: GlasmioOwner? = null
    private val source = GlasmioViewBackdrop(page, this, { dark }, failed)
    private var requested = false
    private val position = IntArray(2)
    private var geometry = ""
    private val beforeDraw = ViewTreeObserver.OnPreDrawListener {
        val visible = isShown && alpha > 0f && page.isShown
        if (visible) {
            ViewGeom.unscaledScreenPos(this, position)
            val key = "${position[0]}:${position[1]}:$width:$height:${ViewGeom.cumulativeScale(this)}"
            val stale = page.isDirty || key != geometry || !requested
            geometry = key
            requested = stale
            if (stale) source.refresh()
        } else requested = false
        true
    }
    init {
        clipChildren = false
        clipToPadding = false
    }
    fun update(tabs: List<GlasmioTabItem<View>>, key: View?, night: Boolean, accent: Int?, content: Int?) {
        if (items != tabs || selected !== key || dark != night || this.accent != accent ||
            contentColor != content || tabs.any { it.key.isDirty }
        ) refreshTabContent()
        blur = configuredBlur()
        items = tabs
        selected = key
        this.accent = accent
        contentColor = content
        if (dark != night) source.refresh()
        dark = night
    }
    private val moduleContext = GlasmioResourceContext(
        context.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY)
            .createConfigurationContext(context.resources.configuration),
        requireNotNull(context.applicationContext) { "Host application context unavailable" },
    )

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeAllViews()
        val local = GlasmioOwner().also { owner = it }
        setViewTreeLifecycleOwner(local)
        setViewTreeSavedStateRegistryOwner(local)
        val compose = ComposeView(moduleContext)
        var firstDraw = true
        try {
            compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            compose.setContent {
                val tabs = items
                val expanded = remember { mutableStateOf(0f) }
                if (onAdd != null) {
                    GlasmioTheme(isDark = dark, colorScheme = glasmioColorScheme(dark)) {
                        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            GlasmioIconButton(onClick = { onAdd.run() }, backdrop = source,
                                size = minOf(maxWidth, maxHeight)) { SfIcon("plus", "发布") }
                        }
                    }
                    return@setContent
                }
                if (tabs.isEmpty()) return@setContent
                val scheme = accent?.let { glasmioColorScheme(dark, Color(it)) } ?: glasmioColorScheme(dark)
                val defaults = defaultGlassColors(dark, scheme.primary)
                val colors = defaults.copy(content = contentColor?.let { Color(it) } ?: defaults.content)
                GlasmioTheme(isDark = dark, colorScheme = scheme, colors = colors) {
                    val drawCell: @androidx.compose.runtime.Composable (Any?) -> Unit = { key ->
                        if (key is View) ComposeCanvas(Modifier.fillMaxSize()) {
                            // Read in the draw phase to invalidate every recorded tab on selection changes.
                            contentRevision.intValue
                            val width = key.width
                            val height = key.height
                            if (key.visibility == View.VISIBLE && width > 0 && height > 0) {
                                val heybox = LiquidGlassModule.app() == HostApp.HEYBOX
                                // HeyBox snapshots are already shrunk to fit the standard bar.
                                // Fit the visible snapshot rather than its padded tab column.
                                val content = if (heybox) (key as? ViewGroup)?.getChildAt(0) else null
                                val contentWidth = content?.width?.takeIf { it > 0 } ?: width
                                val contentHeight = content?.height?.takeIf { it > 0 } ?: height
                                val scale = minOf(if (heybox) 1.30f else 1f,
                                    size.width / contentWidth, size.height / contentHeight)
                                val canvas = drawContext.canvas.nativeCanvas
                                val save = canvas.save()
                                try {
                                    canvas.translate((size.width - scale * width) / 2, (size.height - scale * height) / 2)
                                    canvas.scale(scale, scale)
                                    key.draw(canvas)
                                } finally { canvas.restoreToCount(save) }
                            }
                        }
                    }
                    CompositionLocalProvider(LocalNavigationViewContent provides drawCell) {
                        GlasmioNavigationBar(
                            items = tabs,
                            selectedKey = selected,
                            onSelected = onSelected,
                            onExpand = {},
                            compactProgress = expanded,
                            compactSize = 44.dp,
                            modifier = Modifier.fillMaxWidth().drawWithContent {
                                drawContent()
                                if (firstDraw && size.width > 0f && size.height > 0f) {
                                    firstDraw = false
                                    LiquidGlassModule.log(android.util.Log.INFO, "Glasmio navigation first draw")
                                }
                            },
                            backdrop = source,
                            blurRadius = blur,
                            surfaceHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
                                surfaceHeightPx.takeIf { it > 0 }?.toDp()
                                    ?: io.github.liuran001.mmliquidglass.glasmionav.NavigationBarSurfaceHeight
                            },
                            keepIndicatorCapsule = LiquidGlassModule.app() == HostApp.QQ,
                        )
                    }
                }
            }
            // The native content root has no Compose lifecycle. Keep the recomposer local.
            compose.createComposition(ComposeViewContext(
                view = this,
                compositionContext = createLifecycleAwareWindowRecomposer(lifecycle = local.lifecycle),
                lifecycleOwner = local,
                savedStateRegistryOwner = local,
            ))
            addView(compose, LayoutParams(-1, -1))
            local.start()
        } catch (error: Throwable) {
            compose.disposeComposition()
            removeView(compose)
            local.stop()
            owner = null
            failed(error)
            return
        }
        viewTreeObserver.addOnPreDrawListener(beforeDraw)
    }
    override fun onDetachedFromWindow() {
        if (viewTreeObserver.isAlive) viewTreeObserver.removeOnPreDrawListener(beforeDraw)
        super.onDetachedFromWindow()
        owner?.stop()
        owner = null
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY ||
            MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) setMeasuredDimension(0, 0)
        else super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
    override fun dispatchDraw(canvas: Canvas) {
        val save = canvas.save()
        try { super.dispatchDraw(canvas) }
        catch (error: Throwable) { failed(error) }
        finally { canvas.restoreToCount(save) }
    }
}

// The module is not an Application in the host process. Keep module resources,
// but use the running Application for process-wide services and callbacks.
private class GlasmioResourceContext(
    resourcesContext: Context,
    private val hostApplication: Context,
) : ContextWrapper(resourcesContext) {
    override fun getApplicationContext(): Context = hostApplication

    override fun registerComponentCallbacks(callback: ComponentCallbacks) {
        hostApplication.registerComponentCallbacks(callback)
    }

    override fun unregisterComponentCallbacks(callback: ComponentCallbacks) {
        hostApplication.unregisterComponentCallbacks(callback)
    }
}