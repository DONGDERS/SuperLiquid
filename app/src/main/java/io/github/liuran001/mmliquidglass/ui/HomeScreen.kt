package io.github.liuran001.mmliquidglass.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.liuran001.mmliquidglass.BuildConfig
import io.github.liuran001.mmliquidglass.R
import io.github.liuran001.mmliquidglass.ui.component.material.SegmentedColumn
import io.github.liuran001.mmliquidglass.ui.component.material.SegmentedListItem
import io.github.liuran001.mmliquidglass.ui.screen.settings.SettingsState
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.preference.ArrowPreference

/**
 * Home: KSU's "working" hero status card + an About entry button.
 */
@Composable
fun HomeScreen(onOpenAbout: () -> Unit) {
    val settings by SettingsState.flow.collectAsStateWithLifecycle()
    val connected = Config.serviceConnected

    LScreen(title = stringResource(R.string.nav_home)) {
        // ---- hero status card (KSU StatusCard visual) ----
        val container = if (connected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.errorContainer
        val contentColor = androidx.compose.material3.contentColorFor(container)
        val icon = if (connected) Icons.Filled.CheckCircle else Icons.Filled.Warning
        val title = if (connected) "正常工作中" else "模块未生效"
        val summary = if (connected)
            "v${BuildConfig.VERSION_NAME} · 微信 / QQ / Bilibili Tab 栏液态玻璃"
        else
            "请检查 LSPosed 中模块是否启用并勾选作用域，然后重启对应应用"

        when (LocalUiMode.current) {
            UiMode.Material -> Surface(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                color = container,
                contentColor = contentColor,
                shape = MaterialTheme.shapes.large,
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.size(16.dp))
                    Column {
                        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(summary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            UiMode.Miuix -> MiuixCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(38.dp))
                    Spacer(Modifier.size(14.dp))
                    Column {
                        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                            color = miuixColors().onSurface)
                        Text(summary, fontSize = 13.sp, color = miuixColors().onBackground,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        Spacer12()

        // ---- About entry ----
        when (LocalUiMode.current) {
            UiMode.Material -> SegmentedColumn(
                modifier = Modifier.fillMaxWidth(),
                content = listOf {
                    SegmentedListItem(
                        onClick = onOpenAbout,
                        headlineContent = { Text(stringResource(R.string.about_title)) },
                        supportingContent = {
                            Text(
                                stringResource(R.string.settings_repo_url),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                    )
                }
            )

            UiMode.Miuix -> MiuixCard(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.about_title),
                    summary = stringResource(R.string.settings_repo_url),
                    onClick = onOpenAbout,
                )
            }
        }
    }
}

@Composable
private fun Spacer12() {
    Spacer(modifier = Modifier.size(12.dp))
}
