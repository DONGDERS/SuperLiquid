package io.github.liuran001.mmliquidglass.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.liuran001.mmliquidglass.ui.component.miuix.effect.BgEffectBackground
import io.github.liuran001.mmliquidglass.ui.theme.LocalGlobalShaderBg
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import io.github.neoruaa.glasmio.ui.GlasmioMenu
import io.github.neoruaa.glasmio.ui.GlasmioMenuItem
import io.github.neoruaa.glasmio.ui.GlasmioSwitch
import io.github.neoruaa.glasmio.ui.GlasmioIconButton
import io.github.neoruaa.glasmio.ui.GlasmioSegmentedControl
import io.github.liuran001.mmliquidglass.ui.LocalUiMode
import io.github.liuran001.mmliquidglass.ui.UiMode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
internal fun LiquidScene(content: @Composable () -> Unit) {
    val surface = MaterialTheme.colorScheme.background
    val lightEffects = LocalGlobalShaderBg.current
    GlasmioBackdropHost(
        modifier = Modifier.fillMaxSize(),
        sampledContent = {
            Box(Modifier.matchParentSize().background(surface)) {
                if (lightEffects) BgEffectBackground(
                    dynamicBackground = true, modifier = Modifier.matchParentSize(), isFullSize = true,
                ) {}
            }
        },
        overlayContent = { content() },
    )
}

@Composable
internal fun LiquidCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(modifier, contentAlignment = Alignment.TopStart) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
internal fun LiquidPreference(
    title: String,
    summary: String? = null,
    startAction: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val row: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            startAction?.invoke()
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing()
        }
    }
    if (onClick == null) row() else GlassSurface(Modifier.fillMaxWidth(), onClick = onClick) { row() }
}

@Composable
internal fun LiquidDropdown(
    title: String, items: List<String>, selectedIndex: Int, onSelectedIndexChange: (Int) -> Unit,
    summary: String? = null, startAction: (@Composable () -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    GlasmioMenu(expanded, { expanded = it }, itemCount = items.size, modifier = Modifier.fillMaxWidth(),
        exportChildBackdrop = false,
        anchor = { open ->
            LiquidPreference(title, summary, startAction, onClick = open) {
                items.getOrNull(selectedIndex)?.let {
                    Text(it, modifier = Modifier.widthIn(max = 112.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        },
    ) { backdrop, close ->
        Column(Modifier.verticalScroll(rememberScrollState())) {
            items.forEachIndexed { index, label ->
                GlasmioMenuItem(label, onClick = { close(); onSelectedIndexChange(index) }, backdrop = backdrop,
                    systemName = if (index == selectedIndex) "checkmark" else null)
            }
        }
    }
}

@Composable
internal fun LiquidSwitchPreference(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    summary: String? = null, startAction: (@Composable () -> Unit)? = null) {
    LiquidPreference(title, summary, startAction) { GlasmioSwitch(checked, onCheckedChange) }
}

@Composable internal fun isLiquidTheme() = LocalUiMode.current == UiMode.LiquidGlass

@Composable internal fun ManagerCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (isLiquidTheme()) LiquidCard(modifier, content) else Card(modifier = modifier, content = content)
}

@Composable internal fun ManagerArrow(title: String, summary: String? = null, startAction: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    if (isLiquidTheme()) LiquidPreference(title, summary, startAction, onClick = onClick)
    else ArrowPreference(title = title, summary = summary, startAction = { startAction?.invoke() }, onClick = onClick)
}

@Composable internal fun ManagerDropdown(title: String, items: List<String>, selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit, summary: String? = null, startAction: (@Composable () -> Unit)? = null) {
    if (isLiquidTheme()) LiquidDropdown(title, items, selectedIndex, onSelectedIndexChange, summary, startAction)
    else OverlayDropdownPreference(title = title, items = items, selectedIndex = selectedIndex,
        onSelectedIndexChange = onSelectedIndexChange, summary = summary, startAction = { startAction?.invoke() })
}

@Composable internal fun ManagerSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    summary: String? = null, startAction: (@Composable () -> Unit)? = null) {
    if (isLiquidTheme()) LiquidSwitchPreference(title, checked, onCheckedChange, summary, startAction)
    else SwitchPreference(title = title, checked = checked, onCheckedChange = onCheckedChange,
        summary = summary, startAction = { startAction?.invoke() })
}

@Composable internal fun ManagerIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    if (isLiquidTheme()) GlasmioIconButton(onClick = onClick) { content() }
    else IconButton(onClick = onClick) { content() }
}

@Composable internal fun ManagerSegments(tabs: List<String>, selectedTabIndex: Int, onTabSelected: (Int) -> Unit) {
    if (isLiquidTheme()) GlasmioSegmentedControl(tabs.mapIndexed { i, title -> i to title },
        selectedTabIndex, onTabSelected, modifier = Modifier.fillMaxWidth(), height = 40.dp)
    else TabRow(tabs = tabs, selectedTabIndex = selectedTabIndex, onTabSelected = onTabSelected)
}
