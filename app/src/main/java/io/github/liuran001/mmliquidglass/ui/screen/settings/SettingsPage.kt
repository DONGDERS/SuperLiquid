package io.github.liuran001.mmliquidglass.ui.screen.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import io.github.liuran001.mmliquidglass.R
import io.github.liuran001.mmliquidglass.ui.Config
import io.github.liuran001.mmliquidglass.ui.DisplaySettings
import io.github.liuran001.mmliquidglass.ui.Palette
import io.github.liuran001.mmliquidglass.ui.RestartAlt
import io.github.liuran001.mmliquidglass.ui.LocalUiMode
import io.github.liuran001.mmliquidglass.ui.UiMode
import io.github.liuran001.mmliquidglass.ui.component.material.SegmentedColumn
import io.github.liuran001.mmliquidglass.ui.component.material.SegmentedDropdownItem
import io.github.liuran001.mmliquidglass.ui.component.material.SegmentedListItem
import io.github.liuran001.mmliquidglass.ui.component.material.SegmentedSwitchItem
import io.github.liuran001.mmliquidglass.ui.theme.KernelSUTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Theme settings, mirroring KernelSU's SettingsMaterial/SettingsMiuix theme
 * section: a dropdown row for the UI skin and a "Theme" entry that opens the
 * ColorPalette screen. Reset is a plain button row (not a switch).
 */
@Composable
fun SettingsPage(
    onOpenTheme: () -> Unit,
    onResetDefaults: () -> Unit,
) {
    val uiState by SettingsState.flow.collectAsStateWithLifecycle()
    when (LocalUiMode.current) {
        UiMode.Material -> SettingsPageMaterial(uiState, onOpenTheme, onResetDefaults)
        UiMode.Miuix -> SettingsPageMiuix(uiState, onOpenTheme, onResetDefaults)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPageMaterial(
    uiState: SettingsUiState,
    onOpenTheme: () -> Unit,
    onResetDefaults: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            item {
                SegmentedColumn(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                    content = buildList {
                        add {
                            SegmentedDropdownItem(
                                icon = Icons.Filled.DisplaySettings,
                                title = stringResource(id = R.string.settings_ui_mode),
                                summary = stringResource(id = R.string.settings_ui_mode_summary),
                                items = UiMode.entries.map { it.name },
                                selectedIndex = SettingsState.uiModeIndex(),
                                onItemSelected = {
                                    Config.setUiMode(if (it == 1) UiMode.Material.value else UiMode.Miuix.value)
                                    SettingsState.refresh()
                                }
                            )
                        }
                        add {
                            SegmentedListItem(
                                onClick = onOpenTheme,
                                headlineContent = { Text(stringResource(R.string.settings_theme)) },
                                supportingContent = { Text(stringResource(R.string.settings_theme_summary)) },
                                leadingContent = {
                                    Icon(
                                        Icons.Filled.Palette,
                                        stringResource(R.string.settings_theme)
                                    )
                                },
                            )
                        }
                        add {
                            SegmentedListItem(
                                onClick = onResetDefaults,
                                headlineContent = { Text(stringResource(R.string.settings_reset)) },
                                supportingContent = { Text(stringResource(R.string.settings_reset_summary)) },
                                leadingContent = {
                                    Icon(
                                        Icons.Filled.RestartAlt,
                                        stringResource(R.string.settings_reset)
                                    )
                                },
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsPageMiuix(
    uiState: SettingsUiState,
    onOpenTheme: () -> Unit,
    onResetDefaults: () -> Unit,
) {
    top.yukonga.miuix.kmp.basic.Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 12.dp),
                ) {
                    OverlayDropdownPreference(
                        title = stringResource(R.string.settings_ui_mode),
                        summary = stringResource(R.string.settings_ui_mode_summary),
                        items = UiMode.entries.map { it.name },
                        selectedIndex = SettingsState.uiModeIndex(),
                        onSelectedIndexChange = {
                            Config.setUiMode(if (it == 1) UiMode.Material.value else UiMode.Miuix.value)
                            SettingsState.refresh()
                        },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_theme),
                        summary = stringResource(R.string.settings_theme_summary),
                        onClick = onOpenTheme,
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_reset),
                        summary = stringResource(R.string.settings_reset_summary),
                        onClick = onResetDefaults,
                    )
                }
            }
        }
    }
}
