package io.github.liuran001.mmliquidglass.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import io.github.liuran001.mmliquidglass.R
import io.github.liuran001.mmliquidglass.ui.Config
import io.github.liuran001.mmliquidglass.ui.DisplaySettings
import io.github.liuran001.mmliquidglass.ui.Palette
import io.github.liuran001.mmliquidglass.ui.RestartAlt
import io.github.liuran001.mmliquidglass.ui.LScreen
import io.github.liuran001.mmliquidglass.ui.LocalUiMode
import io.github.liuran001.mmliquidglass.ui.UiMode
import io.github.liuran001.mmliquidglass.ui.component.material.SegmentedColumn
import io.github.liuran001.mmliquidglass.ui.component.material.SegmentedDropdownItem
import io.github.liuran001.mmliquidglass.ui.component.material.SegmentedListItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

@Composable
fun SettingsPage(
    bottomOverlay: Dp,
    onOpenTheme: () -> Unit,
    onResetDefaults: () -> Unit,
) {
    LScreen(title = stringResource(R.string.settings_title), bottomOverlay = bottomOverlay) {
        val uiState by SettingsState.flow.collectAsStateWithLifecycle()
        val selectedIndex = UiMode.entries.indexOf(UiMode.fromValue(uiState.uiMode))
        val selectMode: (Int) -> Unit = { index ->
            Config.setUiMode(UiMode.entries[index].value)
            SettingsState.refresh()
        }
        val appearance = stringResource(R.string.settings_appearance)
        val configuration = stringResource(R.string.settings_config_management)
        when (LocalUiMode.current) {
            UiMode.Material -> {
                MaterialSectionTitle(appearance)
                SegmentedColumn(
                    modifier = Modifier.fillMaxWidth(),
                    content = listOf({
                        SegmentedDropdownItem(
                            icon = Icons.Filled.DisplaySettings,
                            title = stringResource(R.string.settings_ui_mode),
                            summary = stringResource(R.string.settings_ui_mode_summary),
                            items = UiMode.entries.map { it.name },
                            selectedIndex = selectedIndex,
                            onItemSelected = selectMode,
                        )
                    }, {
                        SegmentedListItem(
                            onClick = onOpenTheme,
                            headlineContent = { Text(stringResource(R.string.settings_theme)) },
                            supportingContent = { Text(stringResource(R.string.settings_theme_summary)) },
                            leadingContent = { Icon(Icons.Filled.Palette, null) },
                        )
                    }),
                )
                Spacer(Modifier.height(16.dp))
                MaterialSectionTitle(configuration)
                ConfigBackupRows(onResetDefaults, onDone = { SettingsState.refresh() })
            }
            UiMode.Miuix -> {
                SmallTitle(text = appearance, insideMargin = PaddingValues(16.dp, 8.dp))
                io.github.liuran001.mmliquidglass.ui.component.miuix.ShaderCard(modifier = Modifier.fillMaxWidth()) {
                    OverlayDropdownPreference(
                        title = stringResource(R.string.settings_ui_mode),
                        summary = stringResource(R.string.settings_ui_mode_summary),
                        items = UiMode.entries.map { it.name },
                        selectedIndex = selectedIndex,
                        onSelectedIndexChange = selectMode,
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_theme),
                        summary = stringResource(R.string.settings_theme_summary),
                        onClick = onOpenTheme,
                    )
                }
                Spacer(Modifier.height(12.dp))
                SmallTitle(text = configuration, insideMargin = PaddingValues(16.dp, 8.dp))
                ConfigBackupCard(onResetDefaults, onDone = { SettingsState.refresh() })
            }
        }
    }
}

@Composable
private fun MaterialSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp).semantics { heading() },
    )
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
                // Only the file read is IO. applyJson() writes Config's Compose
                // MutableState (_tabs, _uiMode) and SettingsState's snapshot;
                // doing that off the main thread is undefined for Compose's
                // snapshot system, so the mutation goes back to the UI thread.
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    val applied = text?.let {
                        runCatching { ConfigBackup.applyJson(it) }.getOrDefault(0)
                    } ?: 0
                    toastMain(ctx, if (applied > 0)
                        ctx.getString(R.string.settings_backup_imported, applied)
                    else ctx.getString(R.string.settings_backup_fail))
                    if (applied > 0) onDone()
                }
            }.start()
        }
    }
}

/** Miuix-standard backup card: 导出 / 导入 as arrow rows, one block. */
@Composable
private fun ConfigBackupCard(onResetDefaults: () -> Unit, onDone: () -> Unit) {
    val exportLauncher = rememberConfigExportLauncher()
    val importLauncher = rememberConfigImportLauncher(onDone)
    io.github.liuran001.mmliquidglass.ui.component.miuix.ShaderCard(modifier = Modifier.fillMaxWidth()) {
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
        ArrowPreference(
            title = stringResource(R.string.settings_reset),
            summary = stringResource(R.string.settings_reset_summary),
            onClick = onResetDefaults,
        )
    }
}

/** Material variant of the backup block (segmented rows). */
@Composable
private fun ConfigBackupRows(onResetDefaults: () -> Unit, onDone: () -> Unit) {
    val exportLauncher = rememberConfigExportLauncher()
    val importLauncher = rememberConfigImportLauncher(onDone)
    SegmentedColumn(
        modifier = Modifier.fillMaxWidth(),
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
            add {
                SegmentedListItem(
                    onClick = onResetDefaults,
                    headlineContent = { Text(stringResource(R.string.settings_reset)) },
                    supportingContent = { Text(stringResource(R.string.settings_reset_summary)) },
                    leadingContent = { Icon(Icons.Filled.RestartAlt, null) },
                )
            }
        }
    )
}
