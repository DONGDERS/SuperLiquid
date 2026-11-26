package io.github.liuran001.mmliquidglass.ui

import android.os.Build
import android.provider.Settings
import android.system.Os
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Phone
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.GridView
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
    val model = Build.MODEL.takeUnless { it.isBlank() || it.equals(Build.UNKNOWN, true) }
    val deviceName by produceState<String?>(null, context, refresh) {
        value = withContext(Dispatchers.IO) {
            val properties = listOf(
                "ro.product.marketname", "ro.product.vendor.marketname", "ro.vivo.market.name",
                "ro.vivo.internet.name", "ro.config.marketing_name", "ro.product.market_name",
            )
            val marketingName = properties.firstNotNullOfOrNull { property ->
                runCatching {
                    val process = ProcessBuilder("/system/bin/getprop", property).start()
                    try {
                        process.inputStream.bufferedReader().use { it.readText().trim() }
                    } finally {
                        process.destroy()
                    }
                }.getOrNull()?.takeIf { it.isNotEmpty() && !it.equals(model, true) }
            }
            marketingName ?: runCatching { Settings.Global.getString(context.contentResolver, "device_name") }
                .getOrNull()?.trim()?.takeIf { it.isNotEmpty() && !it.equals(model, true) }
        }
    }
    val device = "${deviceName ?: unknown} - ${model ?: unknown}"
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
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Icon(
                                homeInfoIcon(label, UiMode.Material), contentDescription = null,
                                modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Column(Modifier.weight(1f)) {
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
        }
        UiMode.Miuix -> {
            SmallTitle(text = title, insideMargin = PaddingValues(16.dp, 8.dp))
            io.github.liuran001.mmliquidglass.ui.component.miuix.ShaderCard(modifier = Modifier.fillMaxWidth()) {
                rows.forEach { (label, value) ->
                    SelectionContainer {
                        BasicComponent(
                            title = stringResource(label),
                            summary = value,
                            startAction = {
                                top.yukonga.miuix.kmp.basic.Icon(
                                    homeInfoIcon(label, UiMode.Miuix), contentDescription = null,
                                    modifier = Modifier.size(24.dp).padding(end = 0.dp),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun homeInfoIcon(label: Int, mode: UiMode) = when (mode) {
    UiMode.Material -> when (label) {
        R.string.home_info_device -> Icons.Outlined.Smartphone
        R.string.home_info_kernel -> Icons.Outlined.DeveloperBoard
        R.string.home_info_android -> Icons.Outlined.Android
        R.string.home_info_fingerprint -> Icons.Outlined.Fingerprint
        R.string.home_info_lsp_version -> Icons.Outlined.Security
        R.string.home_lsp_status -> Icons.Outlined.CheckCircle
        else -> Icons.Outlined.Apps
    }
    UiMode.Miuix -> when (label) {
        R.string.home_info_device -> MiuixIcons.Phone
        R.string.home_info_kernel -> MiuixIcons.Layers
        R.string.home_info_android -> MiuixIcons.Info
        R.string.home_info_fingerprint -> MiuixIcons.Scan
        R.string.home_info_lsp_version -> MiuixIcons.Tune
        R.string.home_lsp_status -> MiuixIcons.Ok
        else -> MiuixIcons.GridView
    }
}
