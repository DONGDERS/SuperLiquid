package io.github.liuran001.mmliquidglass.ui

import android.os.Build
import android.provider.Settings
import android.system.Os
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.liuran001.mmliquidglass.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.SmallTitle

private data class FrameworkDetails(val version: String?, val scopeCount: Int?)

@Composable
internal fun HomeInformation() {
    val context = LocalContext.current
    val service = Config.frameworkService
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val details by produceState<FrameworkDetails?>(null, service, refresh) {
        value = null
        if (service != null) {
            value = withContext(Dispatchers.IO) {
                val version = runCatching { service.frameworkVersion }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                val scopeCount = runCatching { service.scope.toSet().size }.getOrNull()
                FrameworkDetails(version, scopeCount)
            }
        }
    }
    val unknown = stringResource(R.string.home_info_unavailable)
    val device = remember(context, refresh) {
        runCatching { Settings.Global.getString(context.contentResolver, "device_name") }
            .getOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: Build.MODEL
    }
    val kernel = remember { runCatching { Os.uname().release }.getOrNull() }
    val status = stringResource(
        when {
            service == null -> R.string.home_lsp_disconnected
            Config.serviceConnected -> R.string.home_lsp_enabled
            else -> R.string.home_lsp_unconfirmed
        },
    )

    HomeInfoSection(
        stringResource(R.string.home_version_information),
        listOf(
            R.string.home_info_device to device,
            R.string.home_info_kernel to (kernel ?: unknown),
            R.string.home_info_android to Build.VERSION.RELEASE,
            R.string.home_info_fingerprint to Build.FINGERPRINT,
            R.string.home_info_lsp_version to (details?.version ?: unknown),
        ),
    )
    Spacer(Modifier.height(12.dp))
    HomeInfoSection(
        stringResource(R.string.home_status),
        listOf(
            R.string.home_lsp_status to status,
            R.string.home_active_app_count to (details?.scopeCount?.toString() ?: unknown),
        ),
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun HomeInfoSection(title: String, rows: List<Pair<Int, String>>) {
    when (LocalUiMode.current) {
        UiMode.Material -> {
            Text(
                text = title,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    rows.forEach { (label, value) ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                            SelectionContainer {
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        UiMode.Miuix -> {
            SmallTitle(text = title, insideMargin = PaddingValues(16.dp, 8.dp))
            MiuixCard(modifier = Modifier.fillMaxWidth()) {
                rows.forEach { (label, value) ->
                    SelectionContainer {
                        BasicComponent(title = stringResource(label), summary = value)
                    }
                }
            }
        }
    }
}
