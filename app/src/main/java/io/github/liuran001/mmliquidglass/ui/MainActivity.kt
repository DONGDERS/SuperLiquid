package io.github.liuran001.mmliquidglass.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import io.github.liuran001.mmliquidglass.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Config.init(applicationContext)
        setContent { SuperLiquidTheme { MainRoot() } }
    }
}

private data class NavEntry(
    val labelRes: Int,
    val filled: ImageVector,
    val outlined: ImageVector,
    val miuix: ImageVector,
)

private val NAV = listOf(
    NavEntry(R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, Icons.Filled.Home),
    NavEntry(R.string.nav_apps, Icons.Filled.Apps, Icons.Outlined.Apps, Icons.Filled.Apps),
    NavEntry(R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings, Icons.Filled.Settings),
)

@Composable
fun MainRoot() {
    val pager = rememberPagerState { NAV.size }
    val scope = rememberCoroutineScope()
    val goto: (Int) -> Unit = { i -> scope.launch { pager.animateScrollToPage(i) } }

    if (LocalUiMode.current == UiMode.MAT) {
        Scaffold(
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
                            label = { Text(stringResource(e.labelRes)) }
                        )
                    }
                }
            }
        ) { pad ->
            HorizontalPager(pager, modifier = Modifier.padding(pad)) { page ->
                when (page) {
                    0 -> HomeScreen()
                    1 -> AppsScreen()
                    else -> SettingsScreen()
                }
            }
        }
    } else {
        MiuixScaffold(
            bottomBar = {
                MiuixNavigationBar { 
                    NAV.forEachIndexed { i, e ->
                        MiuixNavigationBarItem(
                            modifier = Modifier.weight(1f),
                            selected = pager.currentPage == i,
                            onClick = { goto(i) },
                            icon = e.miuix,
                            label = stringResource(e.labelRes),
                        )
                    }
                }
            }
        ) { pad ->
            HorizontalPager(pager, modifier = Modifier.padding(pad)) { page ->
                when (page) {
                    0 -> HomeScreen()
                    1 -> AppsScreen()
                    else -> SettingsScreen()
                }
            }
        }
    }
}
