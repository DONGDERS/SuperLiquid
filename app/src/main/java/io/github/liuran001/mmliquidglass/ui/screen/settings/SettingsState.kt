package io.github.liuran001.mmliquidglass.ui.screen.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import io.github.liuran001.mmliquidglass.data.repository.SettingsRepositoryImpl
import io.github.liuran001.mmliquidglass.ui.AppSettingsStore
import io.github.liuran001.mmliquidglass.ui.UiMode

/**
 * Compose-facing settings snapshot. KSU drives this from a DataStore-backed
 * viewmodel; we back it with our SharedPreferences repository — same fields,
 * same names, so the ported screens compile untouched.
 */
object SettingsState {

    private val _flow = MutableStateFlow(SettingsUiState())
    val flow: StateFlow<SettingsUiState> = _flow

    fun refresh() {
        val r = SettingsRepositoryImpl()
        _flow.value = SettingsUiState(
            uiMode = r.uiMode,
            themeMode = r.themeMode,
            miuixMonet = r.miuixMonet,
            keyColor = r.keyColor,
            colorStyle = r.colorStyle,
            colorSpec = r.colorSpec,
            enablePredictiveBack = r.enablePredictiveBack,
            enableSwipeDismiss = r.enableSwipeDismiss,
            pagerInterceptionMode = r.pagerInterceptionMode,
            enableBlur = r.enableBlur,
            enableFloatingBottomBar = r.enableFloatingBottomBar,
            enableFloatingBottomBarBlur = r.enableFloatingBottomBarBlur,
            enableNavigationBadge = r.enableNavigationBadge,
            pageScale = r.pageScale,
            moduleDescriptionMaxLines = r.moduleDescriptionMaxLines,
        )
        AppSettingsStore.reload()
    }

    fun uiModeIndex(): Int =
        if (flow.value.uiMode == UiMode.Material.value) 1 else 0
}
