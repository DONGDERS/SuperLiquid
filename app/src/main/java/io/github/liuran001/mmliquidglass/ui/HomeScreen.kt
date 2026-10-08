package io.github.liuran001.mmliquidglass.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import io.github.liuran001.mmliquidglass.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.preference.ArrowPreference

/**
 * Home: KSU's "working" hero status card + an About entry button.
 * Miuix branch mirrors KSU HomeMiuix.kt (Scaffold + hero Card with
 * oversized decorative icon + big 22sp title).
 */
@Composable
fun HomeScreen(onOpenAbout: () -> Unit) {
    val settings by SettingsState.flow.collectAsStateWithLifecycle()
    val connected = Config.serviceConnected

    when (LocalUiMode.current) {
        UiMode.Material -> HomeScreenMaterial(connected, onOpenAbout)
        UiMode.Miuix -> HomeScreenMiuix(connected, onOpenAbout)
    }
}

@Composable
private fun HomeScreenMaterial(connected: Boolean, onOpenAbout: () -> Unit) {
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

        Surface(
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

        Spacer12()

        // ---- About entry ----
        SegmentedColumn(
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
    }
}

// KSU HomeMiuix hero card colors (green = working; red = not effective).
private fun heroColors(connected: Boolean, dark: Boolean): Pair<Color, Color> = when {
    connected -> if (dark) Color(0xFF1A3825) to Color(0xFF36D167)
    else Color(0xFFDFFAE4) to Color(0xFF36D167)
    dark -> Color(0xFF3A2320) to Color(0xFFEF9A9A)
    else -> Color(0xFFFCE1E1) to Color(0xFFD32F2F)
}

@Composable
private fun HomeScreenMiuix(connected: Boolean, onOpenAbout: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    MiuixScaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.nav_home),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
            contentPadding = innerPadding,
        ) {
            item {
                Column {
                    val dark = isInDarkTheme()
                    val (bg, iconTint) = heroColors(connected, dark)
                    val title = if (connected) "正常工作中" else "模块未生效"
                    val summary = if (connected)
                        "v${BuildConfig.VERSION_NAME} · 微信 / QQ / Bilibili Tab 栏液态玻璃"
                    else
                        "请检查 LSPosed 中模块是否启用并勾选作用域，然后重启对应应用"
                    // ---- hero status card: KSU HomeMiuix StatusCard ----
                    MiuixCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(color = bg),
                        // KSU StatusCard press feedback; no navigation wired.
                        onClick = {},
                        pressFeedbackType = top.yukonga.miuix.kmp.utils.PressFeedbackType.Tilt,
                        showIndication = true,
                    ) {
                        Box {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .offset(27.dp, 31.dp),
                                contentAlignment = Alignment.BottomEnd,
                            ) {
                                Icon(
                                    modifier = Modifier.size(110.dp),
                                    imageVector = if (connected) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                    tint = iconTint,
                                    contentDescription = null,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp, 14.dp),
                                contentAlignment = Alignment.TopStart,
                            ) {
                                Column {
                                    Text(
                                        text = title,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(1.dp))
                                    Text(text = summary, fontSize = 15.sp)
                                }
                            }
                        }
                    }

                    Spacer12()

                    // ---- About entry ----
                    MiuixCard(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = stringResource(R.string.about_title),
                            summary = stringResource(R.string.settings_repo_url),
                            onClick = onOpenAbout,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Spacer12() {
    Spacer(modifier = Modifier.size(12.dp))
}
