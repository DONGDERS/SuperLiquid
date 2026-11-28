package io.github.liuran001.mmliquidglass.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.liuran001.mmliquidglass.R
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import io.github.neoruaa.glasmio.scaffold.GlasmioNavigationBarWithTrailingItem
import io.github.neoruaa.glasmio.ui.GlasmioTabItem
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.icons.SfSymbol

@Composable
internal fun LiquidBottomBar(
    selected: Int,
    select: (Int) -> Unit,
    backdrop: Backdrop,
    searchProgress: androidx.compose.runtime.State<Float>,
    searchActive: Boolean,
    openSearch: () -> Unit,
    onSearchBounds: (Rect) -> Unit,
) {
    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(
        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
    )).padding(start = 20.dp, end = 20.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
        GlasmioNavigationBarWithTrailingItem(
            items = listOf(
                GlasmioTabItem(0, stringResource(R.string.nav_home), SfSymbol.House),
                GlasmioTabItem(1, stringResource(R.string.nav_apps), SfSymbol.Layers),
                GlasmioTabItem(2, stringResource(R.string.nav_settings), SfSymbol.Gearshape),
            ),
            trailingItem = GlasmioTabItem(3, stringResource(R.string.apps_search), SfSymbol.Search),
            selectedKey = selected,
            onSelected = { if (!searchActive) { if (it == 3) openSearch() else select(it) } },
            onExpand = {},
            compactProgress = rememberUpdatedState(0f),
            onItemBoundsChanged = { key, bounds -> if (key == 3) onSearchBounds(bounds) },
            modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = 1f - searchProgress.value.coerceIn(0f, 1f) },
            backdrop = backdrop,
        )
    }
}
