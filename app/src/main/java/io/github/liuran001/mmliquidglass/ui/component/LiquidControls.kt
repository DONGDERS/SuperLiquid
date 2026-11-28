package io.github.liuran001.mmliquidglass.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.ui.glasmioGroupedListSeparator
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.liuran001.mmliquidglass.ui.component.miuix.effect.BgEffectBackground
import io.github.liuran001.mmliquidglass.ui.theme.LocalGlobalShaderBg
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import io.github.neoruaa.glasmio.glass.LocalBlurBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.glass.glassBackdropSource
import io.github.neoruaa.glasmio.glass.trackBackdropPosition
import io.github.neoruaa.glasmio.ui.GlasmioGroupedList
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
    if (isLiquidTheme()) LiquidScene(opaque = true) {
        val page = rememberLayerBackdrop()
        val background = LocalGlassColors.current.groupedBackground.copy(alpha = 1f)
        CompositionLocalProvider(LocalBlurBackdrop provides page) {
            Box(Modifier.fillMaxSize().glassBackdropSource(page).trackBackdropPosition(page).background(background)) {
                content()
            }
        }
    } else content()
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
    showTopSeparator: Boolean = true,
    showDisclosure: Boolean = onClick != null && trailing == null,
) {
    val colors = LocalGlassColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (summary == null) 52.dp else 68.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (startAction != null) {
            startAction()
            Spacer(Modifier.width(8.dp))
        }
        Row(
            Modifier.weight(1f).heightIn(min = if (summary == null) 52.dp else 68.dp)
                .glasmioGroupedListSeparator(colors.separator, visible = showTopSeparator)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = IosTypography.body, color = colors.content)
                if (summary != null) Text(summary, style = IosTypography.subheadline, color = colors.secondaryContent)
            }
            if (trailing != null) {
                Box(Modifier.widthIn(max = 152.dp), contentAlignment = Alignment.CenterEnd) { trailing() }
            }
            if (showDisclosure) SfIcon("chevron.forward", null, size = 18.dp, mirrored = true, tint = colors.tertiaryContent)
        }
    }
}

@Composable
internal fun LiquidDropdown(
    title: String, items: List<String>, selectedIndex: Int, onSelectedIndexChange: (Int) -> Unit,
    summary: String? = null, startAction: (@Composable () -> Unit)? = null,
) {
    LiquidPreference(title, summary, startAction, trailing = {
        if (items.isNotEmpty()) GlasmioPopUpButton(
            selected = selectedIndex.coerceIn(items.indices),
            items = items.indices.toList(),
            onSelected = onSelectedIndexChange,
            label = { items[it] },
            modifier = Modifier.widthIn(max = 160.dp),
        )
    })
}

@Composable
internal fun LiquidSwitchPreference(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    summary: String? = null, startAction: (@Composable () -> Unit)? = null) {
    LiquidPreference(title, summary, startAction, trailing = { GlasmioSwitch(checked, onCheckedChange) })
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
