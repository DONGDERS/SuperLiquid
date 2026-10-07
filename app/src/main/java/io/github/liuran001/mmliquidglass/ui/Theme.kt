package io.github.liuran001.mmliquidglass.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.LocalActivity
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

private const val BRAND = 0xFF3482FF

/** Mat (Material 3, dynamic color on S+) / Miuix (HyperOS) dual theme root. */
@Composable
fun SuperLiquidTheme(content: @Composable () -> Unit) {
    val dark = when (Config.colorMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    SetSystemBars(dark)
    when (Config.uiModeEnum) {
        UiMode.MAT -> {
            val ctx = LocalContext.current
            val scheme = when {
                Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(ctx)
                Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(ctx)
                dark -> darkColorScheme(primary = Color(BRAND))
                else -> lightColorScheme(primary = Color(BRAND))
            }
            MaterialTheme(colorScheme = scheme) { content() }
        }

        UiMode.MIUIX -> {
            val controller = remember(dark) {
                ThemeController(
                    colorSchemeMode = if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light,
                    keyColor = Color(BRAND),
                )
            }
            MiuixTheme(controller = controller) { content() }
        }
    }
}

@Composable
private fun SetSystemBars(dark: Boolean) {
    val activity = LocalActivity.current ?: return
    val window = activity.window
    androidx.compose.runtime.SideEffect {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
