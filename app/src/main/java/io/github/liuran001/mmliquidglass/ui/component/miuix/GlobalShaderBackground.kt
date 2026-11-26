package io.github.liuran001.mmliquidglass.ui.component.miuix

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
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

val LocalGlobalShaderBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

@Composable
fun GlobalShaderBackground(content: @Composable () -> Unit) {
    if (!LocalGlobalShaderBg.current) {
        content()
        return
    }
    val backdrop = rememberLayerBackdrop()
    CompositionLocalProvider(LocalGlobalShaderBackdrop provides backdrop) {
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

/** Same native Miuix Card and blend tokens as the About page's links card. */
@Composable
fun ShaderCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalGlobalShaderBackdrop.current
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
        colors = CardDefaults.defaultColors(Color.Transparent, Color.Transparent),
        content = content,
    )
}
