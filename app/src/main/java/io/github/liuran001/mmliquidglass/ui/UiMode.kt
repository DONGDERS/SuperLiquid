package io.github.liuran001.mmliquidglass.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Which skin renders the manager. Mirrors KernelSU's UiMode. */
enum class UiMode(val value: String) {
    Miuix("miuix"),
    Material("material");

    companion object {
        fun fromValue(value: String): UiMode =
            if (value == Material.value) Material else Miuix

        const val DEFAULT = "mat"
    }
}

val LocalUiMode = staticCompositionLocalOf { UiMode.Miuix }
