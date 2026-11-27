package io.github.liuran001.mmliquidglass

import android.content.ComponentCallbacks
import android.content.Context
import android.content.ContextWrapper

import android.graphics.Canvas
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.NavigationGlassMaterial
import com.kyant.shapes.Capsule
import androidx.compose.ui.unit.dp

/** Rendering-only child below native tabs; it never consumes their touch events. */
internal class GlasmioPanel(
    context: Context, private val page: ViewGroup, night: Boolean,
    private val failed: (Throwable) -> Unit,
) : FrameLayout(context) {
    private var dark by mutableStateOf(night)
    private var configRevision by mutableIntStateOf(0)
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
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isClickable = false
        isFocusable = false
    }
    fun refresh(night: Boolean) {
        dark = night
        configRevision++
        source.refresh()
    }
    private val moduleContext = GlasmioResourceContext(
        context.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY)
            .createConfigurationContext(context.resources.configuration),
        requireNotNull(context.applicationContext) { "Railway application context unavailable" },
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
                configRevision
                GlasmioTheme(isDark = dark) {
                    GlassSurface(
                        modifier = Modifier.fillMaxSize().drawWithContent {
                            drawContent()
                            if (firstDraw && size.width > 0f && size.height > 0f) {
                                firstDraw = false
                                LiquidGlassModule.log(android.util.Log.INFO, "Glasmio Railway first draw")
                            }
                        },
                        backdrop = source, shape = Capsule(),
                        material = NavigationGlassMaterial.RegularSmall,
                        frostRadius = (5f * GlassConfig.blurRadius.coerceIn(1, 8) / 3f).dp,
                        navigationSurfaceAlphaMultiplier = GlassConfig.tintStrength.coerceIn(.2f, 2f),
                    ) {}
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
    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
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