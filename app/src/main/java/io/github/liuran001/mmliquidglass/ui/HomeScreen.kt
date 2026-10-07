package io.github.liuran001.mmliquidglass.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.liuran001.mmliquidglass.R
import io.github.liuran001.mmliquidglass.ui.component.material.SegmentedColumn
import io.github.liuran001.mmliquidglass.ui.component.material.SegmentedListItem
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference

/**
 * Home: one big "running" status card (KSU's home hero card) + an About entry
 * (button, per request — the old about text card is gone).
 */
@Composable
fun HomeScreen(onOpenAbout: () -> Unit) {
    LScreen(title = stringResource(R.string.nav_home)) {
        // hero status card
        LCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .padding(end = 14.dp)
                        .size(52.dp)
                )
                Column {
                    LText(stringResource(R.string.home_status_title))
                    LText(
                        if (Config.serviceConnected)
                            stringResource(R.string.home_service_ok)
                        else
                            stringResource(R.string.home_service_no),
                        subtitle = true
                    )
                }
            }
            Spacer12()
            LText(stringResource(R.string.home_scope), subtitle = true)
            LText(stringResource(R.string.home_scope_value), subtitle = true)
        }

        // About entry — a button, both skins
        when (LocalUiMode.current) {
            UiMode.Material -> SegmentedColumn(
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

            UiMode.Miuix -> MiuixCard(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.about_title),
                    summary = stringResource(R.string.settings_repo_url),
                    onClick = onOpenAbout,
                )
            }
        }
    }
}

@Composable
private fun Spacer12() {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(12.dp))
}
