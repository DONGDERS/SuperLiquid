package io.github.liuran001.mmliquidglass.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun LScreen(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontSize = if (LocalUiMode.current == UiMode.Material) 32.sp else 36.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.padding(top = 48.dp, bottom = 12.dp)
        )
        content()
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
            .padding(vertical = 4.dp),
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
            modifier = Modifier.fillMaxWidth(),
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
