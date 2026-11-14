package io.github.liuran001.mmliquidglass.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Which skin renders the manager. Mirrors KernelSU's UiMode. */
enum class UiMode(val value: String) {
    Miuix("miuix"),
    Material("material");

    companion object {
        fun fromValue(value: String): UiMode =
            if (value == Material.value) Material else Miuix

        /** Must be one of the two real enum values: "mat" was neither, so
         *  fromValue() fell through to Miuix while ThemeController's
         *  `uiMode == "miuix"` test saw something else entirely. */
        const val DEFAULT = "miuix"
    }
}

val LocalUiMode = staticCompositionLocalOf { UiMode.Miuix }
