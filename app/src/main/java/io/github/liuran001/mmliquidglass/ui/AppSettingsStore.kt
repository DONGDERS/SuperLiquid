package io.github.liuran001.mmliquidglass.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import io.github.liuran001.mmliquidglass.data.repository.SettingsRepositoryImpl
import io.github.liuran001.mmliquidglass.ui.theme.AppSettings
import io.github.liuran001.mmliquidglass.ui.theme.ColorMode
import io.github.liuran001.mmliquidglass.ui.theme.ThemeController

/**
 * Compose-visible snapshot of theme settings. Every settings writer calls
 * [reload] so the ported KernelSU theme layer recomposes instantly — this is
 * what makes the Mat/Miuix switch a real theme swap, not a recolor.
 */
object AppSettingsStore {
    var state by mutableStateOf(
        AppSettings(ColorMode.SYSTEM, 0xFF3482FF.toInt(), PaletteStyle.TonalSpot, ColorSpec.SpecVersion.SPEC_2025)
    )
        private set

    fun reload() {
        state = ThemeController.getAppSettings(SettingsRepositoryImpl())
    }
}
