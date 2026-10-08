package io.github.liuran001.mmliquidglass.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.liuran001.mmliquidglass.ui.component.FloatingBottomBar
import io.github.liuran001.mmliquidglass.ui.component.FloatingBottomBarItem
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.liuran001.mmliquidglass.R
import io.github.liuran001.mmliquidglass.ui.screen.settings.SettingsState
import io.github.liuran001.mmliquidglass.ui.theme.LocalColorMode
import io.github.liuran001.mmliquidglass.ui.theme.LocalEnableBlur
import io.github.liuran001.mmliquidglass.ui.theme.LocalEnableFloatingBottomBar
import io.github.liuran001.mmliquidglass.ui.theme.LocalEnableFloatingBottomBarBlur
import io.github.liuran001.mmliquidglass.ui.theme.LocalEnableNavigationBadge
import io.github.liuran001.mmliquidglass.ui.theme.LocalModuleDescriptionMaxLines
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Config.init(applicationContext)
        SettingsState.refresh()
        setContent {
            val appSettings = AppSettingsStore.state
            val settings by SettingsState.flow.collectAsStateWithLifecycle()
            val uiMode = Config.uiModeEnum
            // Pager state lives OUTSIDE the theme-switched subtree: the Mat/
            // Miuix swap re-parents everything below KernelSUTheme, and a
            // pager remembered inside it would reset to page 0 on each flip.
            val pagerState = rememberPagerState(initialPage = 0) { NAV.size }
            CompositionLocalProvider(
                LocalUiMode provides uiMode,
                LocalColorMode provides appSettings.colorMode.value,
                LocalEnableBlur provides settings.enableBlur,
                LocalEnableFloatingBottomBar provides settings.enableFloatingBottomBar,
                LocalEnableFloatingBottomBarBlur provides settings.enableFloatingBottomBarBlur,
                LocalEnableNavigationBadge provides settings.enableNavigationBadge,
                LocalModuleDescriptionMaxLines provides settings.moduleDescriptionMaxLines,
            ) {
                io.github.liuran001.mmliquidglass.ui.theme.KernelSUTheme(appSettings = appSettings) {
                    MainRoot(pagerState)
                }
            }
        }
    }
}

private data class NavEntry(
    val labelRes: Int,
    val filled: ImageVector,
    val outlined: ImageVector,
)

// 2×2 grid "Apps" glyph — avoids pulling material-icons-extended (tens of
// thousands of vector classes) into R8 just for one icon.
private val AppsIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Apps",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black)
        ) {
            moveTo(4f, 4f); lineTo(10f, 4f); lineTo(10f, 10f); lineTo(4f, 10f); close()
            moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f); lineTo(14f, 10f); close()
            moveTo(4f, 14f); lineTo(10f, 14f); lineTo(10f, 20f); lineTo(4f, 20f); close()
            moveTo(14f, 14f); lineTo(20f, 14f); lineTo(20f, 20f); lineTo(14f, 20f); close()
        }
    }.build()
}

private val NAV = listOf(
    NavEntry(R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    NavEntry(R.string.nav_apps, AppsIcon, AppsIcon),
    NavEntry(R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun MainRoot(pager: PagerState) {
    val scope = rememberCoroutineScope()
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showColorPalette by rememberSaveable { mutableStateOf(false) }

    when {
        showAbout -> {
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            val state = io.github.liuran001.mmliquidglass.ui.screen.about.AboutUiState(
                title = stringResource(R.string.about_title),
                appName = stringResource(R.string.app_name),
                versionName = io.github.liuran001.mmliquidglass.BuildConfig.VERSION_NAME,
                links = listOf(
                    io.github.liuran001.mmliquidglass.ui.screen.about.LinkInfo(
                        "GitHub · DONGDERS/SuperLiquid",
                        stringResource(R.string.settings_repo_url)
                    ),
                    io.github.liuran001.mmliquidglass.ui.screen.about.LinkInfo(
                        "原项目 · liuran001/WeChat-LiquidGlass",
                        "https://github.com/liuran001/WeChat-LiquidGlass"
                    ),
                ),
            )
            val actions = io.github.liuran001.mmliquidglass.ui.screen.about.AboutScreenActions(
                onBack = { showAbout = false },
                onOpenLink = { uriHandler.openUri(it) },
            )
            io.github.liuran001.mmliquidglass.ui.screen.about.AboutScreenMaterial(state, actions)
        }

        showColorPalette -> {
            io.github.liuran001.mmliquidglass.ui.screen.colorpalette.ColorPaletteScreen(
                onBack = { showColorPalette = false }
            )
        }

        else -> {
            val settings = SettingsState.flow.collectAsStateWithLifecycle().value
            val enableFloating = settings.enableFloatingBottomBar
            // KSU MainScreen: the backdrop carries a surface base fill so an
            // uncaptured/empty layer reads as the theme surface, NOT black —
            // the bare rememberLayerBackdrop() was the grey-glass root cause.
            val backdropSurface = when (LocalUiMode.current) {
                UiMode.Miuix -> top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surface
                UiMode.Material -> androidx.compose.material3.MaterialTheme.colorScheme.surface
            }
            val pageBackdrop =
                top.yukonga.miuix.kmp.blur.rememberLayerBackdrop {
                    drawRect(backdropSurface)
                    drawContent()
                }
            // KSU: capture only when the floating glass bar actually blurs.
            val captureGlass = enableFloating && settings.enableFloatingBottomBarBlur
            val blurBackdrop =
                io.github.liuran001.mmliquidglass.ui.util.rememberBlurBackdrop(settings.enableBlur)
            android.util.Log.println(android.util.Log.INFO, "SuperLiquid",
                "dongder.super.liquid glass wiring: uiMode=" + LocalUiMode.current
                        + " floating=" + enableFloating
                        + " glassToggle=" + settings.enableFloatingBottomBarBlur
                        + " capture=" + captureGlass
                        + " shaderSupported=" + top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported())

            // One shared pager for both skins: the theme switch swaps only the
            // Scaffold wrapper, the pager never leaves composition.
            val pagerContent: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit = { pad ->
                // KSU-exact capture structure: the backdrop modifier lives on
                // a wrapping Box, NOT on the pager itself — a scroll container
                // as the record node captures empty layers on some pipelines.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(pad)
                        .then(
                            if (captureGlass) Modifier.layerBackdrop(pageBackdrop)
                            else Modifier
                        )
                        .then(
                            if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop)
                            else Modifier
                        )
                ) {
                    HorizontalPager(
                        pager,
                        beyondViewportPageCount = 2,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> HomeScreen(onOpenAbout = { showAbout = true })
                            1 -> AppsScreen()
                            else -> io.github.liuran001.mmliquidglass.ui.screen.settings.SettingsPage(
                                onOpenTheme = { showColorPalette = true },
                                onResetDefaults = { Config.resetDefaults() },
                            )
                        }
                    }
                }
            }

            // KSU MainPagerState pattern: the pill position is set IMMEDIATELY
            // on tap and re-synced from the pager only when it settles.
            // Binding to pager.currentPage double-drove the pill (its own
            // animation + the pager's halfway snap) — the "bounce" at the end
            // of every move.
            var selectedPage by androidx.compose.runtime.saveable.rememberSaveable {
                androidx.compose.runtime.mutableIntStateOf(0)
            }
            androidx.compose.runtime.LaunchedEffect(pager) {
                androidx.compose.runtime.snapshotFlow { pager.settledPage }
                    .collect { selectedPage = it }
            }

            fun goto(i: Int) {
                selectedPage = i
                scope.launch { pager.animateScrollToPage(i) }
            }

            val floatingBar: @Composable () -> Unit = {
                // Centre the hugging pill on screen; FloatingBottomBar hugs
                // its tabs (IntrinsicSize.Min) and would otherwise sit at
                // the container's start edge.
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    FloatingBottomBar(
                        selectedIndex = selectedPage,
                        onSelected = { goto(it) },
                        backdrop = pageBackdrop,
                        tabsCount = NAV.size,
                        isBlurEnabled = settings.enableFloatingBottomBarBlur,
                    ) { activateTab ->
                        NAV.forEachIndexed { i, e ->
                            FloatingBottomBarItem(
                                selected = selectedPage == i,
                                onClick = { activateTab(i) },
                                modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(e.filled, contentDescription = null, modifier = Modifier.size(22.dp))
                                    Text(stringResource(e.labelRes), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            when (LocalUiMode.current) {
                // The floating glass bar is a Miuix-skin feature (KSU parity):
                // it reads MiuixTheme, which the Mat branch does not provide.
                UiMode.Material -> Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NAV.forEachIndexed { i, e ->
                                NavigationBarItem(
                                    selected = pager.currentPage == i,
                                    onClick = { goto(i) },
                                    icon = {
                                        Icon(
                                            if (pager.currentPage == i) e.filled else e.outlined,
                                            contentDescription = null
                                        )
                                    },
                                    label = { Text(stringResource(e.labelRes)) },
                                )
                            }
                        }
                    }
                ) { pad -> pagerContent(pad) }

                UiMode.Miuix -> MiuixScaffold(
                    bottomBar = {
                        if (enableFloating) floatingBar() else {
                            // KSU BottomBarMiuix non-floating branch: the bar
                            // blurs the content behind it via BlurredBar.
                            io.github.liuran001.mmliquidglass.ui.util.BlurredBar(backdrop = blurBackdrop) {
                                MiuixNavigationBar(
                                    color = if (blurBackdrop != null) androidx.compose.ui.graphics.Color.Transparent
                                    else top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surface,
                                ) {
                                    NAV.forEachIndexed { i, e ->
                                        MiuixNavigationBarItem(
                                            modifier = Modifier.weight(1f),
                                            selected = pager.currentPage == i,
                                            onClick = { goto(i) },
                                            icon = e.filled,
                                            label = stringResource(e.labelRes),
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) { pad -> pagerContent(pad) }
            }
        }
    }
}
