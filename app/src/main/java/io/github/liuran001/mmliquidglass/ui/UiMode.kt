package io.github.liuran001.mmliquidglass.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Which skin renders the manager. Mirrors KernelSU's UiMode. */
enum class UiMode(val value: String) {
    Miuix("miuix"),
    Material("material"),
    LiquidGlass("liquid_glass");

    val supportsLightEffects: Boolean get() = this != Material

    companion object {
        fun fromValue(value: String): UiMode =
            entries.firstOrNull { it.value == value } ?: Miuix

        const val DEFAULT = "miuix"
    }
}

val LocalUiMode = staticCompositionLocalOf { UiMode.Miuix }
