package io.github.liuran001.mmliquidglass.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.liuran001.mmliquidglass.ui.component.MainTabScaffold
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun miuixColors() = MiuixTheme.colorScheme

@Composable
fun stringResourceCompat(res: Int): String = androidx.compose.ui.res.stringResource(res)

@Composable
fun LScreen(
    title: String,
    bottomOverlay: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    MainTabScaffold(title) { modifier, spacing ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = if (LocalUiMode.current == UiMode.Miuix) 12.dp else 16.dp,
                    end = if (LocalUiMode.current == UiMode.Miuix) 12.dp else 16.dp,
                    top = spacing.calculateTopPadding(),
                    bottom = 24.dp + bottomOverlay,
                ),
            content = content,
        )
    }
}

@Composable
fun LCard(content: @Composable ColumnScope.() -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Material -> androidx.compose.material3.Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) { Column(Modifier.padding(16.dp)) { content() } }

        UiMode.Miuix -> MiuixCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) { Column(Modifier.padding(16.dp)) { content() } }
    }
}

@Composable
fun LText(text: String, subtitle: Boolean = false) {
    when (LocalUiMode.current) {
        UiMode.Material -> Text(
            text,
            fontSize = if (subtitle) 14.sp else 16.sp,
            color = if (subtitle) {
                androidx.compose.material3.MaterialTheme.colorScheme.onBackground
            } else {
                androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(vertical = 2.dp)
        )

        UiMode.Miuix -> MiuixText(
            text,
            fontSize = if (subtitle) 14.sp else 16.sp,
            color = if (subtitle) miuixColors().onBackground else miuixColors().onSurface,
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }
}

@Composable
fun LSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = if (LocalUiMode.current == UiMode.Miuix) 12.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            LText(title)
            if (subtitle != null) LText(subtitle, subtitle = true)
        }
        when (LocalUiMode.current) {
            UiMode.Material -> Switch(checked = checked, onCheckedChange = onChange)
            UiMode.Miuix -> MiuixSwitch(checked = checked, onCheckedChange = onChange)
        }
    }
}

/** Tappable row (title + summary), used for "open editor" entries. */
@Composable
fun LActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = if (LocalUiMode.current == UiMode.Miuix) 12.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            LText(title)
            LText(subtitle, subtitle = true)
        }
    }
}

@Composable
fun LSliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    format: (Float) -> String = { v ->
        if (v == v.toInt().toFloat()) v.toInt().toString() else String.format("%.2f", v)
    },
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (LocalUiMode.current == UiMode.Miuix) 12.dp else 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LText(title)
            LText(format(value))
        }
        when (LocalUiMode.current) {
            UiMode.Material -> Slider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                steps = steps,
                modifier = Modifier.fillMaxWidth()
            )

            UiMode.Miuix -> MiuixSlider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 10.dp, start = 12.dp, end = 12.dp)
            )
        }
    }
}
