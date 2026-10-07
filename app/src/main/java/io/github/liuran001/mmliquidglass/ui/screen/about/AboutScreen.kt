package io.github.liuran001.mmliquidglass.ui.screen.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import io.github.liuran001.mmliquidglass.BuildConfig
import io.github.liuran001.mmliquidglass.R
import io.github.liuran001.mmliquidglass.ui.LocalUiMode
import io.github.liuran001.mmliquidglass.ui.UiMode
import androidx.compose.ui.res.stringResource

/**
 * Builds our About state and hands it to the verbatim-ported KernelSU
 * AboutMaterial / AboutMiuix pages.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val state = AboutUiState(
        title = stringResource(R.string.about_title),
        appName = stringResource(R.string.app_name),
        versionName = BuildConfig.VERSION_NAME,
        links = listOf(
            LinkInfo(
                "GitHub · DONGDERS/SuperLiquid",
                stringResource(R.string.settings_repo_url)
            ),
            LinkInfo(
                "原项目 · liuran001/WeChat-LiquidGlass",
                "https://github.com/liuran001/WeChat-LiquidGlass"
            ),
        ),
    )
    val actions = AboutScreenActions(
        onBack = onBack,
        onOpenLink = { uriHandler.openUri(it) },
    )
    when (LocalUiMode.current) {
        UiMode.Miuix -> AboutScreenMiuix(state, actions)
        UiMode.Material -> AboutScreenMaterial(state, actions)
    }
}
