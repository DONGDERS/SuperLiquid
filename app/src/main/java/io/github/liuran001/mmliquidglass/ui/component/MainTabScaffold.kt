package io.github.liuran001.mmliquidglass.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.github.liuran001.mmliquidglass.ui.LocalUiMode
import io.github.liuran001.mmliquidglass.ui.UiMode
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar

/** Each pager page owns its title and scroll state. The outer scaffold owns the bottom bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainTabScaffold(
    title: String,
    content: @Composable (Modifier, PaddingValues) -> Unit,
) {
    val insets = WindowInsets(0, 0, 0, 0)
    when (LocalUiMode.current) {
        UiMode.Material -> {
            val scroll = TopAppBarDefaults.pinnedScrollBehavior()
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = { Text(title) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        scrollBehavior = scroll,
                    )
                },
                contentWindowInsets = insets,
            ) { bars ->
                content(Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection)
                    .padding(bars).consumeWindowInsets(bars), PaddingValues(top = 16.dp))
            }
        }
        UiMode.Miuix -> {
            val scroll = MiuixScrollBehavior()
            MiuixScaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = { MiuixTopAppBar(title = title, scrollBehavior = scroll) },
                contentWindowInsets = insets,
            ) { bars ->
                content(Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection)
                    .padding(bars).consumeWindowInsets(bars), PaddingValues(top = 8.dp))
            }
        }
    }
}
