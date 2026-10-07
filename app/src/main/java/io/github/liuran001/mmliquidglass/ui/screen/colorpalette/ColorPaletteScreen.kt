package io.github.liuran001.mmliquidglass.ui.screen.colorpalette

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import io.github.liuran001.mmliquidglass.data.repository.SettingsRepositoryImpl
import io.github.liuran001.mmliquidglass.ui.LocalUiMode
import io.github.liuran001.mmliquidglass.ui.UiMode
import io.github.liuran001.mmliquidglass.ui.screen.settings.SettingsState
import io.github.liuran001.mmliquidglass.ui.theme.ColorMode

/**
 * Our variant of KSU's ColorPaletteScreen: the viewmodel is replaced by the
 * SharedPreferences-backed SettingsState — the Material/Miuix pages below are
 * the verbatim-ported KSU screens.
 */
@Composable
fun ColorPaletteScreen(onBack: () -> Unit) {
    val repo = SettingsRepositoryImpl()
    val uiState by SettingsState.flow.collectAsStateWithLifecycle()
    val currentPaletteStyle = try {
        PaletteStyle.valueOf(uiState.colorStyle)
    } catch (_: Exception) {
        PaletteStyle.TonalSpot
    }
    val currentColorSpec = try {
        ColorSpec.SpecVersion.valueOf(uiState.colorSpec)
    } catch (_: Exception) {
        ColorSpec.SpecVersion.SPEC_2025
    }
    val state = ColorPaletteUiState(
        uiState = uiState,
        currentColorMode = ColorMode.fromValue(uiState.themeMode),
        currentPaletteStyle = currentPaletteStyle,
        currentColorSpec = currentColorSpec,
    )
    val actions = ColorPaletteScreenActions(
        onBack = onBack,
        onSetThemeMode = { repo.themeMode = it; SettingsState.refresh() },
        onSetMiuixMonet = { repo.miuixMonet = it; SettingsState.refresh() },
        onSetKeyColor = { repo.keyColor = it; SettingsState.refresh() },
        onSetColorMode = { repo.themeMode = it.value; SettingsState.refresh() },
        onSetColorStyle = { repo.colorStyle = it; SettingsState.refresh() },
        onSetColorSpec = { repo.colorSpec = it; SettingsState.refresh() },
        onSetEnableBlur = { repo.enableBlur = it; SettingsState.refresh() },
        onSetEnableFloatingBottomBar = { repo.enableFloatingBottomBar = it; SettingsState.refresh() },
        onSetEnableFloatingBottomBarBlur = { repo.enableFloatingBottomBarBlur = it; SettingsState.refresh() },
        onSetEnableNavigationBadge = { repo.enableNavigationBadge = it; SettingsState.refresh() },
        onSetEnablePredictiveBack = { repo.enablePredictiveBack = it; SettingsState.refresh() },
        onSetEnableSwipeDismiss = { repo.enableSwipeDismiss = it; SettingsState.refresh() },
        onSetPagerInterceptionMode = { repo.pagerInterceptionMode = it; SettingsState.refresh() },
        onSetPageScale = { repo.pageScale = it; SettingsState.refresh() },
        onSetModuleDescriptionMaxLines = { repo.moduleDescriptionMaxLines = it; SettingsState.refresh() },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> ColorPaletteScreenMiuix(state, actions)
        UiMode.Material -> ColorPaletteScreenMaterial(state, actions)
    }
}
