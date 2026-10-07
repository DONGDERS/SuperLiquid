package io.github.liuran001.mmliquidglass.ui

import android.content.Intent
import android.net.Uri
import io.github.liuran001.mmliquidglass.BuildConfig
import io.github.liuran001.mmliquidglass.R
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen() {
    LScreen(title = stringResourceCompat(R.string.nav_home)) {
        LCard {
            LText(stringResourceCompat(R.string.home_status_title), subtitle = true)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                LText(stringResourceCompat(R.string.home_version))
                Spacer(Modifier.weight(1f))
                LText("v" + BuildConfig.VERSION_NAME)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                LText(stringResourceCompat(R.string.home_scope))
                Spacer(Modifier.weight(1f))
                LText(stringResourceCompat(R.string.home_scope_value))
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                LText("LSPosed")
                Spacer(Modifier.weight(1f))
                LText(
                    if (Config.serviceConnected) stringResourceCompat(R.string.home_service_ok)
                    else stringResourceCompat(R.string.home_service_no)
                )
            }
        }
        LCard {
            LText(stringResourceCompat(R.string.home_about_title), subtitle = true)
            LText(stringResourceCompat(R.string.home_about_body), subtitle = true)
            val ctx = LocalContext.current
            val repoUrl = stringResourceCompat(R.string.settings_repo_url)
            TextButton(onClick = {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl)))
            }) {
                Text(
                    stringResourceCompat(R.string.home_repo),
                    color = if (LocalUiMode.current == UiMode.MAT) MaterialTheme.colorScheme.primary
                    else top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.primary,
                    fontSize = 14.sp, fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun stringResourceCompat(res: Int): String = androidx.compose.ui.res.stringResource(res)
