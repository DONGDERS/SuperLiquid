package io.github.liuran001.mmliquidglass.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Which skin renders the manager. Mirrors KernelSU's UiMode. */
enum class UiMode(val value: String) {
    MAT("mat"),
    MIUIX("miuix");

    companion object {
        fun fromValue(value: String): UiMode =
            if (value == MIUIX.value) MIUIX else MAT

        const val DEFAULT = "mat"
    }
}

val LocalUiMode = staticCompositionLocalOf { UiMode.MAT }
