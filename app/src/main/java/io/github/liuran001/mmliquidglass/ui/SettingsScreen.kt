package io.github.liuran001.mmliquidglass.ui

import androidx.compose.ui.Modifier
import io.github.liuran001.mmliquidglass.R
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun SettingsScreen() {
    LScreen(title = stringResourceCompat(R.string.settings_title)) {
        LCard {
            LText(stringResourceCompat(R.string.settings_appearance), subtitle = true)

            LText(stringResourceCompat(R.string.settings_ui_mode), subtitle = true)
            Row(Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = Config.uiModeEnum == UiMode.MAT,
                    onClick = { Config.setUiMode(UiMode.MAT.value) },
                    label = { Text(stringResourceCompat(R.string.settings_ui_mode_mat)) }
                )
                FilterChip(
                    selected = Config.uiModeEnum == UiMode.MIUIX,
                    onClick = { Config.setUiMode(UiMode.MIUIX.value) },
                    label = { Text(stringResourceCompat(R.string.settings_ui_mode_miuix)) }
                )
            }

            LText(stringResourceCompat(R.string.settings_color_mode), subtitle = true)
            Row(Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = Config.colorMode == 0,
                    onClick = { Config.setColorMode(0) },
                    label = { Text(stringResourceCompat(R.string.settings_color_follow)) }
                )
                FilterChip(
                    selected = Config.colorMode == 1,
                    onClick = { Config.setColorMode(1) },
                    label = { Text(stringResourceCompat(R.string.settings_color_light)) }
                )
                FilterChip(
                    selected = Config.colorMode == 2,
                    onClick = { Config.setColorMode(2) },
                    label = { Text(stringResourceCompat(R.string.settings_color_dark)) }
                )
            }
        }
        LCard {
            LSwitchRow(
                stringResourceCompat(R.string.settings_reset), null, false
            ) { if (it) Config.resetDefaults() }
        }
    }
}
