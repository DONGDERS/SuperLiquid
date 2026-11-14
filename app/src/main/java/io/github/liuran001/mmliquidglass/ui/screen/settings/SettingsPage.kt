package io.github.liuran001.mmliquidglass.ui.screen.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
            item {
                ConfigBackupRows(onDone = { SettingsState.refresh() })
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
            // Config backup: one dedicated block (JSON file via SAF).
            item {
                ConfigBackupCard(onDone = { SettingsState.refresh() })
            }
        }
    }
}

// ---- config backup (export/import as one JSON file, SAF) ----------------

private fun toastMain(ctx: android.content.Context, msg: String) {
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun rememberConfigExportLauncher(): androidx.activity.compose.ManagedActivityResultLauncher<String, android.net.Uri?> {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            Thread {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(ConfigBackup.buildJson().toByteArray())
                    }
                    toastMain(ctx, ctx.getString(R.string.settings_backup_done))
                }.onFailure {
                    toastMain(ctx, ctx.getString(R.string.settings_backup_fail))
                }
            }.start()
        }
    }
}

@Composable
private fun rememberConfigImportLauncher(
    onDone: () -> Unit
): androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, android.net.Uri?> {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            Thread {
                val text = runCatching {
                    ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                val applied = text?.let { runCatching { ConfigBackup.applyJson(it) }.getOrDefault(0) } ?: 0
                toastMain(ctx, if (applied > 0) ctx.getString(R.string.settings_backup_imported, applied)
                          else ctx.getString(R.string.settings_backup_fail))
                if (applied > 0) onDone()
            }.start()
        }
    }
}

/** Miuix-standard backup card: 导出 / 导入 as arrow rows, one block. */
@Composable
fun ConfigBackupCard(onDone: () -> Unit) {
    val exportLauncher = rememberConfigExportLauncher()
    val importLauncher = rememberConfigImportLauncher(onDone)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        ArrowPreference(
            title = stringResource(R.string.settings_backup_export),
            summary = stringResource(R.string.settings_backup_export_summary),
            onClick = { exportLauncher.launch("superliquid-config.json") },
        )
        ArrowPreference(
            title = stringResource(R.string.settings_backup_import),
            summary = stringResource(R.string.settings_backup_import_summary),
            onClick = {
                importLauncher.launch(arrayOf("application/json", "text/plain", "text/*"))
            },
        )
    }
}

/** Material variant of the backup block (segmented rows). */
@Composable
fun ConfigBackupRows(onDone: () -> Unit) {
    val exportLauncher = rememberConfigExportLauncher()
    val importLauncher = rememberConfigImportLauncher(onDone)
    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        content = buildList {
            add {
                SegmentedListItem(
                    onClick = { exportLauncher.launch("superliquid-config.json") },
                    headlineContent = { Text(stringResource(R.string.settings_backup_export)) },
                    supportingContent = { Text(stringResource(R.string.settings_backup_export_summary)) },
                    leadingContent = { Icon(Icons.Filled.Check, null) },
                )
            }
            add {
                SegmentedListItem(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "text/*")) },
                    headlineContent = { Text(stringResource(R.string.settings_backup_import)) },
                    supportingContent = { Text(stringResource(R.string.settings_backup_import_summary)) },
                    leadingContent = { Icon(Icons.Filled.Refresh, null) },
                )
            }
        }
    )
}
