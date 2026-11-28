package io.github.liuran001.mmliquidglass.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.liuran001.mmliquidglass.ui.component.miuix.effect.BgEffectBackground
import io.github.liuran001.mmliquidglass.ui.theme.LocalGlobalShaderBg
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import io.github.neoruaa.glasmio.ui.GlasmioGroupedList
import io.github.neoruaa.glasmio.ui.GlasmioListRow
import io.github.neoruaa.glasmio.ui.GlasmioPopUpButton
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
internal fun ManagerDetailPage(content: @Composable () -> Unit) {
    if (isLiquidTheme()) LiquidScene(opaque = true, content = content) else content()
}

@Composable
internal fun LiquidScene(opaque: Boolean = false, content: @Composable () -> Unit) {
    val surface = LocalGlassColors.current.groupedBackground.copy(alpha = 1f)
    val lightEffects = !opaque && LocalGlobalShaderBg.current
    GlasmioBackdropHost(
        modifier = Modifier.fillMaxSize().then(if (opaque) Modifier.background(surface) else Modifier),
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
    GlasmioGroupedList(modifier = modifier, content = content)
}

@Composable
internal fun LiquidPreference(
    title: String,
    summary: String? = null,
    startAction: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    GlasmioListRow(title, subtitle = summary, leading = startAction, onClick = onClick, trailing = trailing)
}

@Composable
internal fun LiquidDropdown(
    title: String, items: List<String>, selectedIndex: Int, onSelectedIndexChange: (Int) -> Unit,
    summary: String? = null, startAction: (@Composable () -> Unit)? = null,
) {
    LiquidPreference(title, summary, startAction) {
        if (items.isNotEmpty()) GlasmioPopUpButton(
            selected = selectedIndex.coerceIn(items.indices),
            items = items.indices.toList(),
            onSelected = onSelectedIndexChange,
            label = { items[it] },
            modifier = Modifier.widthIn(max = 160.dp),
        )
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
