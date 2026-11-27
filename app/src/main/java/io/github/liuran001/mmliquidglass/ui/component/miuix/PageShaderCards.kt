package io.github.liuran001.mmliquidglass.ui.component.miuix

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.liuran001.mmliquidglass.ui.component.miuix.effect.BgEffectBackground
import io.github.liuran001.mmliquidglass.ui.component.miuix.effect.ColorBlendToken
import io.github.liuran001.mmliquidglass.ui.theme.LocalGlobalShaderBg
import io.github.liuran001.mmliquidglass.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur

val LocalPageShaderBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

@Composable
fun PageShaderBackground(content: @Composable BoxScope.() -> Unit) {
    if (!LocalGlobalShaderBg.current) {
        Box(Modifier.fillMaxSize(), content = content)
        return
    }
    val backdrop = rememberLayerBackdrop()
    CompositionLocalProvider(LocalPageShaderBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize()) {
            BgEffectBackground(
                dynamicBackground = true,
                modifier = Modifier.matchParentSize(),
                bgModifier = Modifier.layerBackdrop(backdrop),
                isFullSize = true,
                effectBackground = true,
            ) {}
            content()
        }
    }
}

/** About-style glass confined to the main pager; content keeps its theme color. */
@Composable
fun ShaderCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalPageShaderBackdrop.current
    if (backdrop == null) {
        Card(modifier = modifier, content = content)
        return
    }
    val blendColors = if (isInDarkTheme()) ColorBlendToken.Overlay_Thin_Light
        else ColorBlendToken.Pured_Regular_Light
    Card(
        modifier = modifier.textureBlur(
            backdrop = backdrop,
            shape = RoundedCornerShape(16.dp),
            blurRadius = 60f,
            colors = BlurColors(blendColors = blendColors),
            enabled = true,
        ),
        colors = CardDefaults.defaultColors(color = Color.Transparent),
        content = content,
    )
}
