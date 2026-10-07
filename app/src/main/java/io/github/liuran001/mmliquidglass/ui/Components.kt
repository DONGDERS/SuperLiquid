package io.github.liuran001.mmliquidglass.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Skin-agnostic building blocks. Every page is a scrolled column of cards;
 * the Mat/Miuix split only swaps the card/switch/text styling.
 */

@Composable
fun LScreen(title: String, content: @Composable ColumnScope.() -> Unit) {
    val mat = LocalUiMode.current == UiMode.MAT
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontSize = if (mat) 32.sp else 36.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.padding(top = 48.dp, bottom = 12.dp)
        )
        content()
    }
}

@Composable
fun LCard(content: @Composable ColumnScope.() -> Unit) {
    if (LocalUiMode.current == UiMode.MAT) {
        androidx.compose.material3.Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) { Column(Modifier.padding(16.dp), content = content) }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) { Column(Modifier.padding(16.dp), content = content) }
    }
}

@Composable
fun LText(text: String, subtitle: Boolean = false) {
    val mat = LocalUiMode.current == UiMode.MAT
    val color = if (mat) {
        androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    } else MiuixTheme.colorScheme.onSurface
    Text(
        text = text,
        style = TextStyle(
            fontSize = if (subtitle) 13.sp else 16.sp,
            color = if (subtitle) color.copy(alpha = 0.6f) else color,
        ),
        modifier = Modifier.padding(vertical = if (subtitle) 0.dp else 2.dp)
    )
}

@Composable
fun LSwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
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
        Switch(checked = checked, onCheckedChange = onChange)
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
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
