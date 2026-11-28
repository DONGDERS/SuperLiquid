package io.github.liuran001.mmliquidglass.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.liuran001.mmliquidglass.R
import io.github.liuran001.mmliquidglass.glasmionav.GlasmioNavigationBar
import io.github.liuran001.mmliquidglass.glasmionav.GlasmioTabItem
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberCombinedBackdrop
import io.github.neoruaa.glasmio.icons.SfSymbol

@Composable
internal fun LiquidBottomBar(selected: Int, select: (Int) -> Unit, page: Backdrop) {
    val backdrop = rememberCombinedBackdrop(LocalGlassBackdrop.current, page)
    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(
        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
    )).padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        GlasmioNavigationBar(
            items = listOf(
                GlasmioTabItem(0, stringResource(R.string.nav_home), SfSymbol.House),
                GlasmioTabItem(1, stringResource(R.string.nav_apps), SfSymbol.Layers),
                GlasmioTabItem(2, stringResource(R.string.nav_settings), SfSymbol.Gearshape),
            ),
            selectedKey = selected, onSelected = select, onExpand = {},
            compactProgress = rememberUpdatedState(0f), compactSize = 48.dp,
            backdrop = backdrop,
        )
    }
}
