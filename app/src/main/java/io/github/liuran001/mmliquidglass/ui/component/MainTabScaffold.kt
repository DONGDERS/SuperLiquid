package io.github.liuran001.mmliquidglass.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.liuran001.mmliquidglass.ui.LocalUiMode
import io.github.liuran001.mmliquidglass.ui.UiMode
import io.github.liuran001.mmliquidglass.ui.theme.LocalEnableBlur
import io.github.liuran001.mmliquidglass.ui.util.BlurredBar
import io.github.liuran001.mmliquidglass.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Each pager page owns its title and scroll state. The outer scaffold owns the bottom bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainTabScaffold(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    belowTopBar: @Composable () -> Unit = {},
    content: @Composable (Modifier, PaddingValues) -> Unit,
) {
    val insets = WindowInsets(0, 0, 0, 0)
    val mode = LocalUiMode.current
    val globalShader = io.github.liuran001.mmliquidglass.ui.theme.LocalGlobalShaderBg.current
    val surface = when (mode) {
        UiMode.Material -> MaterialTheme.colorScheme.surface
        UiMode.Miuix -> MiuixTheme.colorScheme.surface
    }
    val backdrop = rememberBlurBackdrop(LocalEnableBlur.current && !globalShader, surface)
    when (mode) {
        UiMode.Material -> {
            val scroll = TopAppBarDefaults.pinnedScrollBehavior()
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = if (globalShader) Color.Transparent else MaterialTheme.colorScheme.background,
                topBar = {
                    BlurredBar(backdrop, surface = surface) {
                        Column {
                            TopAppBar(
                                title = { Text(title) },
                                actions = actions,
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = if (backdrop != null) Color.Transparent else surface,
                                    scrolledContainerColor = if (backdrop != null) Color.Transparent
                                    else MaterialTheme.colorScheme.surfaceContainer,
                                ),
                                scrollBehavior = scroll,
                            )
                            belowTopBar()
                        }
                    }
                },
                contentWindowInsets = insets,
            ) { bars ->
                TabBody(backdrop, bars, 16.dp, scroll.nestedScrollConnection, content)
            }
        }
        UiMode.Miuix -> {
            val scroll = MiuixScrollBehavior()
            MiuixScaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = if (globalShader) Color.Transparent else MiuixTheme.colorScheme.surface,
                topBar = {
                    BlurredBar(backdrop, surface = surface) {
                        MiuixTopAppBar(
                            title = title,
                            actions = actions,
                            bottomContent = belowTopBar,
                            color = if (globalShader || backdrop != null) Color.Transparent else surface,
                            scrollBehavior = scroll,
                        )
                    }
                },
                contentWindowInsets = insets,
            ) { bars ->
                TabBody(backdrop, bars, 8.dp, scroll.nestedScrollConnection, content)
            }
        }
    }
}

@Composable
private fun TabBody(
    backdrop: LayerBackdrop?,
    bars: PaddingValues,
    gap: Dp,
    scroll: NestedScrollConnection,
    content: @Composable (Modifier, PaddingValues) -> Unit,
) {
    // Record the scroll viewport, not the bar itself. The initial top inset
    // belongs inside the scroll content so rows can pass behind the bar.
    Box(Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
        content(
            Modifier.fillMaxSize().nestedScroll(scroll),
            PaddingValues(top = bars.calculateTopPadding() + gap),
        )
    }
}
