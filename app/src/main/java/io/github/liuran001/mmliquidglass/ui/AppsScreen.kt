package io.github.liuran001.mmliquidglass.ui

import androidx.compose.runtime.collectAsState
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import io.github.liuran001.mmliquidglass.R
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import androidx.compose.foundation.lazy.items
import io.github.liuran001.mmliquidglass.ui.component.LiquidPreference
import io.github.neoruaa.glasmio.ui.GlasmioGroupedList
import androidx.compose.ui.graphics.Color
import io.github.neoruaa.glasmio.scaffold.GlasmioPinnedListPage
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Search
import io.github.liuran001.mmliquidglass.ui.component.ManagerArrow

private data class Target(val key: String, val label: String, val pkg: String)

private val TARGETS = listOf(
    Target("mm", "微信", "com.tencent.mm"),
    Target("qq", "QQ", "com.tencent.mobileqq"),
    Target("bili", "哔哩哔哩", "tv.danmaku.bili"),
    Target("heybox", "小黑盒", "com.max.xiaoheihe"),
    Target("kugou", "酷狗音乐", "com.kugou.android"),
    Target("pinduoduo", "拼多多", "com.xunmeng.pinduoduo"),
    Target("taobao", "淘宝", "com.taobao.taobao"),
    Target("alipay", "支付宝", "com.eg.android.AlipayGphone"),
    Target("railway", "铁路 12306", "com.MobileTicket"),
)

private data class AppIdentity(val installed: Boolean, val label: String?, val icon: ImageBitmap?)

@Composable
private fun rememberAppIdentities(): Map<String, AppIdentity> {
    val pm = LocalContext.current.packageManager
    val iconPx = with(LocalDensity.current) { 38.dp.roundToPx() }.coerceAtLeast(1)
    return remember(pm, iconPx) {
        TARGETS.associate { target ->
            val info = try {
                pm.getApplicationInfo(target.pkg, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
            val label = info?.loadLabel(pm)?.toString()
            val icon = info?.let {
                runCatching {
                    val drawable = it.loadIcon(pm)
                    val bitmap = Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888)
                    drawable.setBounds(0, 0, iconPx, iconPx)
                    drawable.draw(Canvas(bitmap))
                    bitmap.asImageBitmap()
                }.getOrNull()
            }
            target.key to AppIdentity(info != null, label, icon)
        }
    }
}

@Composable
fun AppsScreen(bottomOverlay: Dp, floatingQuery: String? = null, resultsPadding: PaddingValues = PaddingValues()) {
    var tabDialogKey by remember { mutableStateOf<String?>(null) }
    var tabDialogVisible by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val liquid = LocalUiMode.current == UiMode.LiquidGlass
    val identities = rememberAppIdentities()
    val cardState = rememberSaveableStateHolder()
    val uiSettings by io.github.liuran001.mmliquidglass.ui.screen.settings.SettingsState.flow.collectAsState()
    // Expanded cards in open order (oldest first); the limit collapses the oldest.
    var expandedKeys by rememberSaveable { mutableStateOf(listOf<String>()) }
    val expandLimit = if (uiSettings.enableAppExpandLimit) uiSettings.appExpandLimit.coerceAtLeast(1) else Int.MAX_VALUE
    LaunchedEffect(expandLimit) { if (expandedKeys.size > expandLimit) expandedKeys = expandedKeys.takeLast(expandLimit) }
    fun toggleCard(k: String) {
        expandedKeys = if (k in expandedKeys) expandedKeys - k else (expandedKeys + k).takeLast(expandLimit)
    }
    val term = (floatingQuery ?: if (liquid) "" else query).trim()
    val targets = TARGETS.filter { t ->
        t.label.contains(term, ignoreCase = true) || t.pkg.contains(term, ignoreCase = true) ||
            identities.getValue(t.key).label?.contains(term, ignoreCase = true) == true ||
            (t.key == "bili" && "Bilibili".contains(term, ignoreCase = true)) ||
            (t.key == "mm" && "WeChat".contains(term, ignoreCase = true)) ||
            (t.key == "heybox" && "Heybox".contains(term, ignoreCase = true))
    }
    tabDialogKey?.let { appKey ->
        key(appKey) {
            TabLayoutDialog(
                appKey = appKey,
                show = tabDialogVisible,
                onApply = { cfg -> Config.setTabLayout(appKey, cfg) },
                onDismiss = { tabDialogVisible = false },
                onDismissFinished = { if (!tabDialogVisible) tabDialogKey = null },
            )
        }
    }
    val app: @Composable (Target) -> Unit = { t ->
        cardState.SaveableStateProvider(t.key) {
            AppCard(t, identities.getValue(t.key), t.key in expandedKeys, { toggleCard(t.key) }) {
            val cfg = when (t.key) {
                "mm" -> Config.mm
                "qq" -> Config.qq
                "heybox" -> Config.heybox
                "kugou" -> Config.kugou
                "pinduoduo" -> Config.pinduoduo
                "taobao" -> Config.taobao
                "alipay" -> Config.alipay
                "railway" -> Config.railway
                else -> Config.bili.base
            }
            val tabs = RuntimeTabs.snapshot(t.key)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                LText(tabs.status, subtitle = true)
                if (tabs.current) LText(
                    tabs.items.joinToString(" · ") { item ->
                        Config.tabLayout(t.key).takeIf { it.catalogToken == tabs.token }
                            ?.labels?.get(item.key) ?: item.label
                    }, subtitle = true,
                )
            }
            AppParams(
                cfg = cfg,
                set = { Config.setApp(t.key, it) },
                // The Heybox pill overruns its frame at a width of 1.0.
                widthRange = if (t.key == "heybox") 0.2f..0.9f else 0.5f..1.2f,
                onEditTabs = {
                    tabDialogKey = t.key
                    tabDialogVisible = true
                },
            )
            when (t.key) {
                "railway" -> LSliderRow(
                    stringResourceCompat(R.string.apps_railway_notice_lift),
                    Config.railwayNoticeLiftDp.toFloat(),
                    0f..48f,
                    steps = 47,
                ) { Config.setRailwayNoticeLiftDp(it.toInt()) }
                "kugou" -> LSliderRow(
                    stringResourceCompat(R.string.apps_kugou_player_lift),
                    Config.kugouPlayerLiftDp.toFloat(),
                    0f..48f,
                    steps = 47,
                ) { Config.setKugouPlayerLiftDp(it.toInt()) }
                "bili" -> PlusParams(Config.bili.plus) { Config.setBili(Config.bili.copy(plus = it)) }
                "heybox" -> PlusParams(Config.heyboxPlus, Config::setHeyboxPlus)
                else -> Unit
            }
        }
        }
    }
    val list: @Composable ColumnScope.() -> Unit = {
        if (targets.isEmpty()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                LText(stringResourceCompat(R.string.apps_search_empty), subtitle = true)
            }
        }
        targets.forEach { t ->
            key(t.key) {
                app(t)
                if (LocalUiMode.current != UiMode.Material) Spacer(Modifier.height(12.dp))
            }
        }
    }
    if (floatingQuery != null) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(resultsPadding).padding(horizontal = 16.dp),
            content = list,
        )
    } else if (liquid) {
        val effects = io.github.liuran001.mmliquidglass.ui.theme.LocalGlobalShaderBg.current
        GlasmioPinnedListPage(
            title = stringResourceCompat(R.string.apps_title),
            bottomPadding = bottomOverlay,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            backgroundColor = if (effects) Color.Transparent else null,
        ) {
            items(targets, key = { it.key }) { app(it) }
        }
    } else {
        LScreen(
            title = stringResourceCompat(R.string.apps_title),
            bottomOverlay = bottomOverlay,
            actions = {
                if (!liquid) AppSearchButton(searchOpen) {
                    searchOpen = !searchOpen
                    if (!searchOpen) query = ""
                }
            },
            belowTopBar = { if (!liquid && searchOpen) AppSearchField(query) { query = it } },
            content = list,
        )
    }
}

@Composable
private fun AppSearchButton(open: Boolean, onClick: () -> Unit) {
    val description = stringResourceCompat(if (open) R.string.apps_search_close else R.string.apps_search)
    when (LocalUiMode.current) {
        UiMode.Material -> IconButton(onClick = onClick) {
            Icon(if (open) Icons.Filled.Close else Icons.Filled.Search, description)
        }
        UiMode.LiquidGlass -> Unit
        UiMode.Miuix -> MiuixIconButton(onClick = onClick, minWidth = 48.dp, minHeight = 48.dp) {
            MiuixIcon(if (open) MiuixIcons.Close else MiuixIcons.Search, description, Modifier.size(24.dp))
        }
    }
}

@Composable
private fun AppSearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        .focusRequester(focusRequester)
    val hint = stringResourceCompat(R.string.apps_search_hint)
    when (LocalUiMode.current) {
        UiMode.Material -> OutlinedTextField(
            value = query, onValueChange = onQueryChange, modifier = modifier,
            placeholder = { LText(hint, subtitle = true) }, singleLine = true,
        )
        UiMode.LiquidGlass -> Unit
        UiMode.Miuix -> MiuixTextField(
            value = query, onValueChange = onQueryChange, modifier = modifier,
            label = hint, useLabelAsPlaceholder = true, singleLine = true,
        )
    }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
}

@Composable
private fun AppCard(
    t: Target,
    identity: AppIdentity,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val title = t.label + (if (identity.installed) "" else " · " + stringResourceCompat(R.string.apps_not_installed))
    val toggle = onToggle
    when (LocalUiMode.current) {
        UiMode.LiquidGlass -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlasmioGroupedList {
                LiquidPreference(
                    title, summary = t.pkg, startAction = { AppIcon(identity.icon) },
                    onClick = toggle, showDisclosure = false, showTopSeparator = false,
                    trailing = { Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null) },
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            }
        }
        UiMode.Miuix -> io.github.liuran001.mmliquidglass.ui.component.miuix.ShaderCard(modifier = Modifier.fillMaxWidth()) {
            ManagerArrow(
                title = title,
                summary = t.pkg,
                startAction = { AppIcon(identity.icon) },
                onClick = toggle,
            )
            AnimatedVisibility(visible = expanded) {
                Column(content = content)
            }
        }

        UiMode.Material -> Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = toggle)
                    .heightIn(min = 70.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                AppIcon(identity.icon)
                Column(Modifier.weight(1f)) {
                    LText(title)
                    LText(t.pkg, subtitle = true)
                }
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), content = content)
            }
        }
    }
}

@Composable
private fun AppIcon(bitmap: ImageBitmap?) {
    val modifier = Modifier.size(38.dp)
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = modifier)
    } else when (LocalUiMode.current) {
        UiMode.Material -> Icon(Icons.Filled.Apps, null, modifier, MaterialTheme.colorScheme.onSurfaceVariant)
        UiMode.Miuix, UiMode.LiquidGlass -> MiuixIcon(MiuixIcons.GridView, null, modifier)
    }
}

@Composable
private fun AppParams(
    cfg: Config.AppCfg,
    set: (Config.AppCfg) -> Unit,
    widthRange: ClosedFloatingPointRange<Float>,
    onEditTabs: (() -> Unit)? = null
) {
    val liquid = LocalUiMode.current == UiMode.LiquidGlass
    Column(verticalArrangement = Arrangement.spacedBy(if (liquid) 12.dp else 0.dp)) {
        // No per-app enable switch: LSPosed's own toggle already gates the
        // module; a second switch here was redundant state.
        run {
            // Tab layout editor leads the panel on every host.
            if (!liquid && onEditTabs != null) {
                LActionRow(stringResourceCompat(R.string.apps_tab_layout),
                    stringResourceCompat(R.string.apps_tab_layout_summary), onEditTabs)
            }
            val modes = listOf("follow", "standard", "extreme")
            val items = listOf(stringResourceCompat(R.string.performance_follow),
                stringResourceCompat(R.string.performance_standard), stringResourceCompat(R.string.performance_extreme))
            val title = stringResourceCompat(R.string.performance_title)
            val summary = stringResourceCompat(R.string.performance_summary)
            val index = modes.indexOf(cfg.performance).coerceAtLeast(0)
            val select: (Int) -> Unit = { set(cfg.copy(performance = modes[it])) }
            when (LocalUiMode.current) {
                UiMode.Material -> io.github.liuran001.mmliquidglass.ui.component.material.SegmentedDropdownItem(
                    title = title, summary = summary, items = items, selectedIndex = index, onItemSelected = select,
                )
                UiMode.LiquidGlass -> GlasmioGroupedList {
                    if (onEditTabs != null) LiquidPreference(
                        title = stringResourceCompat(R.string.apps_tab_layout),
                        summary = stringResourceCompat(R.string.apps_tab_layout_summary),
                        showTopSeparator = false,
                        onClick = onEditTabs,
                    )
                    io.github.liuran001.mmliquidglass.ui.component.ManagerDropdown(
                        title = title, summary = summary, items = items,
                        selectedIndex = index, onSelectedIndexChange = select,
                    )
                }
                UiMode.Miuix -> io.github.liuran001.mmliquidglass.ui.component.ManagerDropdown(
                    title = title, summary = summary, items = items, selectedIndex = index, onSelectedIndexChange = select,
                )
            }
            LSliderRow(stringResourceCompat(R.string.apps_width_scale), cfg.widthScale, widthRange) {
                set(cfg.copy(widthScale = (it * 100).toInt() / 100f))
            }
            LSliderRow(stringResourceCompat(R.string.apps_pill_scale), cfg.pillScale, 0.1f..3f) {
                set(cfg.copy(pillScale = (it * 100).toInt() / 100f))
            }
            LSliderRow(stringResourceCompat(R.string.apps_bar_offset), cfg.offsetDp.toFloat(), 0f..24f, steps = 23) {
                set(cfg.copy(offsetDp = it.toInt()))
            }
            LSliderRow(stringResourceCompat(R.string.apps_bar_offset_x), cfg.offsetXdp.toFloat(), -120f..120f) {
                set(cfg.copy(offsetXdp = it.toInt()))
            }
            LSliderRow(stringResourceCompat(R.string.apps_blur), cfg.blur.toFloat(), 1f..8f, steps = 6) {
                set(cfg.copy(blur = it.toInt()))
            }
            LSliderRow(stringResourceCompat(R.string.apps_tint), cfg.tint, 0.2f..2f) {
                set(cfg.copy(tint = (it * 100).toInt() / 100f))
            }
            LSliderRow(stringResourceCompat(R.string.apps_saturation), cfg.saturation, 1.0f..1.3f) {
                set(cfg.copy(saturation = (it * 100).toInt() / 100f))
            }
            LSliderRow(stringResourceCompat(R.string.apps_gloss), cfg.gloss, 0f..2f) {
                set(cfg.copy(gloss = (it * 100).toInt() / 100f))
            }
            LSliderRow(stringResourceCompat(R.string.apps_border), cfg.border, 0.5f..2f) {
                set(cfg.copy(border = (it * 100).toInt() / 100f))
            }
        }
    }
}

@Composable
private fun PlusParams(cfg: Config.PlusCfg, set: (Config.PlusCfg) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(
            top = 12.dp,
            start = if (LocalUiMode.current == UiMode.Miuix) 12.dp else 0.dp,
        ),
    ) {
        LText(stringResourceCompat(R.string.apps_plus_section), subtitle = true)
    }
    LSliderRow(stringResourceCompat(R.string.apps_plus_scale), cfg.scale, 0.5f..2f) {
        set(cfg.copy(scale = (it * 100).toInt() / 100f))
    }
    LSliderRow(stringResourceCompat(R.string.apps_plus_bottom), cfg.bottomDp.toFloat(), 0f..48f, steps = 47) {
        set(cfg.copy(bottomDp = it.toInt()))
    }
    LSliderRow(stringResourceCompat(R.string.apps_plus_side), cfg.sideDp.toFloat(), 0f..64f, steps = 63) {
        set(cfg.copy(sideDp = it.toInt()))
    }
}
