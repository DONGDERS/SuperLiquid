package io.github.liuran001.mmliquidglass.ui

import android.content.pm.PackageManager
import io.github.liuran001.mmliquidglass.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext

private data class Target(val key: String, val label: String, val pkg: String)

private val TARGETS = listOf(
    Target("mm", "微信", "com.tencent.mm"),
    Target("qq", "QQ", "com.tencent.mobileqq"),
    Target("bili", "哔哩哔哩", "tv.danmaku.bili"),
)

@Composable
private fun installed(key: String): Boolean {
    val pm = LocalContext.current.packageManager
    val pkg = TARGETS.first { it.key == key }.pkg
    return try {
        pm.getPackageInfo(pkg, 0); true
    } catch (t: Throwable) {
        false
    }
}

@Composable
fun AppsScreen() {
    val collapsed = Config.appParamsCollapsedState.value
    var expandedOverride by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val showParams = !collapsed || expandedOverride
    LScreen(title = stringResourceCompat(R.string.apps_title)) {
        TARGETS.forEach { t ->
            when (t.key) {
                "mm" -> AppCard(t, Config.mm, installed("mm"), collapsed, showParams, { expandedOverride = it }) { Config.setApp("mm", it) }
                "qq" -> AppCard(t, Config.qq, installed("qq"), collapsed, showParams, { expandedOverride = it }) { Config.setApp("qq", it) }
                else -> BiliCard(t, Config.bili, installed("bili"), collapsed, showParams, { expandedOverride = it }) { Config.setBili(it) }
            }
        }
    }
}

@Composable
private fun AppCard(
    t: Target,
    cfg: Config.AppCfg,
    installed: Boolean,
    collapsed: Boolean,
    showParams: Boolean,
    onToggleParams: (Boolean) -> Unit,
    set: (Config.AppCfg) -> Unit
) {
    LCard {
        LText(t.label + (if (installed) "" else " · " + stringResourceCompat(R.string.apps_not_installed)), subtitle = true)
        LSwitchRow(
            stringResourceCompat(R.string.apps_enable), null, cfg.enabled
        ) { set(cfg.copy(enabled = it)) }
        if (collapsed) {
            LSwitchRow(
                if (showParams) "收起参数" else stringResourceCompat(R.string.apps_params_collapse),
                null, showParams
            ) { onToggleParams(it) }
        }
        if (cfg.enabled && showParams) {
            LSliderRow(
                stringResourceCompat(R.string.apps_width_scale), cfg.widthScale, 0.5f..1.2f
            ) { set(cfg.copy(widthScale = (it * 100).toInt() / 100f)) }
            LSliderRow(
                stringResourceCompat(R.string.apps_bar_offset), cfg.offsetDp.toFloat(), 0f..24f, steps = 23
            ) { set(cfg.copy(offsetDp = it.toInt())) }
            LSliderRow(
                stringResourceCompat(R.string.apps_blur), cfg.blur.toFloat(), 1f..8f, steps = 6
            ) { set(cfg.copy(blur = it.toInt())) }
            LSliderRow(
                stringResourceCompat(R.string.apps_tint), cfg.tint, 0.2f..2f
            ) { set(cfg.copy(tint = (it * 100).toInt() / 100f)) }
            LSliderRow(
                stringResourceCompat(R.string.apps_pill_scale), cfg.pillScale, 0.7f..1.3f
            ) { set(cfg.copy(pillScale = (it * 100).toInt() / 100f)) }
            LSliderRow(
                stringResourceCompat(R.string.apps_saturation), cfg.saturation, 1.0f..1.3f
            ) { set(cfg.copy(saturation = (it * 100).toInt() / 100f)) }
            LSliderRow(
                stringResourceCompat(R.string.apps_gloss), cfg.gloss, 0f..2f
            ) { set(cfg.copy(gloss = (it * 100).toInt() / 100f)) }
            LSliderRow(
                stringResourceCompat(R.string.apps_border), cfg.border, 0.5f..2f
            ) { set(cfg.copy(border = (it * 100).toInt() / 100f)) }
        }
    }
}

@Composable
private fun BiliCard(
    t: Target,
    cfg: Config.BiliCfg,
    installed: Boolean,
    collapsed: Boolean,
    showParams: Boolean,
    onToggleParams: (Boolean) -> Unit,
    set: (Config.BiliCfg) -> Unit
) {
    val base = cfg.base
    LCard {
        LText(t.label + (if (installed) "" else " · " + stringResourceCompat(R.string.apps_not_installed)), subtitle = true)
        LSwitchRow(
            stringResourceCompat(R.string.apps_enable), null, base.enabled
        ) { set(cfg.copy(base = base.copy(enabled = it))) }
        if (collapsed) {
            LSwitchRow(
                if (showParams) "收起参数" else stringResourceCompat(R.string.apps_params_collapse),
                null, showParams
            ) { onToggleParams(it) }
        }
        if (base.enabled && showParams) {
            LSliderRow(
                stringResourceCompat(R.string.apps_width_scale), base.widthScale, 0.5f..1.2f
            ) { set(cfg.copy(base = base.copy(widthScale = (it * 100).toInt() / 100f))) }
            LSliderRow(
                stringResourceCompat(R.string.apps_bar_offset), base.offsetDp.toFloat(), 0f..24f, steps = 23
            ) { set(cfg.copy(base = base.copy(offsetDp = it.toInt()))) }
            LSliderRow(
                stringResourceCompat(R.string.apps_blur), base.blur.toFloat(), 1f..8f, steps = 6
            ) { set(cfg.copy(base = base.copy(blur = it.toInt()))) }
            LSliderRow(
                stringResourceCompat(R.string.apps_tint), base.tint, 0.2f..2f
            ) { set(cfg.copy(base = base.copy(tint = (it * 100).toInt() / 100f))) }
            LSliderRow(
                stringResourceCompat(R.string.apps_pill_scale), base.pillScale, 0.7f..1.3f
            ) { set(cfg.copy(base = base.copy(pillScale = (it * 100).toInt() / 100f))) }
            LSliderRow(
                stringResourceCompat(R.string.apps_saturation), base.saturation, 1.0f..1.3f
            ) { set(cfg.copy(base = base.copy(saturation = (it * 100).toInt() / 100f))) }
            LSliderRow(
                stringResourceCompat(R.string.apps_gloss), base.gloss, 0f..2f
            ) { set(cfg.copy(base = base.copy(gloss = (it * 100).toInt() / 100f))) }
            LSliderRow(
                stringResourceCompat(R.string.apps_border), base.border, 0.5f..2f
            ) { set(cfg.copy(base = base.copy(border = (it * 100).toInt() / 100f))) }

            LText(stringResourceCompat(R.string.apps_bili_section), subtitle = true)
            LSwitchRow(
                stringResourceCompat(R.string.apps_bili_hover_tint), null, cfg.hoverTint
            ) { set(cfg.copy(hoverTint = it)) }
            LSwitchRow(
                stringResourceCompat(R.string.apps_bili_hide_native), null, cfg.hideNative
            ) { set(cfg.copy(hideNative = it)) }
            LSliderRow(
                stringResourceCompat(R.string.apps_bili_pub_scale), cfg.pubScale, 0.5f..2f
            ) { set(cfg.copy(pubScale = (it * 100).toInt() / 100f)) }
            LSliderRow(
                stringResourceCompat(R.string.apps_bili_pub_bottom), cfg.pubBottomDp.toFloat(), 0f..48f, steps = 47
            ) { set(cfg.copy(pubBottomDp = it.toInt())) }
            LSliderRow(
                stringResourceCompat(R.string.apps_bili_pub_side), cfg.pubSideDp.toFloat(), 0f..64f, steps = 63
            ) { set(cfg.copy(pubSideDp = it.toInt())) }
        }
    }
}
