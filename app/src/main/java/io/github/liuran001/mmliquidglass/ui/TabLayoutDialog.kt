package io.github.liuran001.mmliquidglass.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * heybox floating-bar tab layout editor: drag to reorder, tap the eye to
 * hide/restore. Locked basics (首页/主页, 我/我的) can never be hidden — the
 * hook enforces the same rule server-side, this is the visible half.
 *
 * The bar's titles are server-driven; the editor edits the known candidate
 * set (stored config entries + defaults). Unlisted runtime tabs simply ride
 * at the end (hook falls back to native order for them).
 */
object TabLayoutEditor {

    /** Known tab titles per app key; unmatched runtime tabs ride at the end. */
    val DEFAULTS = mapOf(
        "mm" to listOf("微信", "通讯录", "发现", "我"),
        "qq" to listOf("消息", "联系人", "动态", "我"),
        "bili" to listOf("首页", "动态", "会员购", "我的"),
        "heybox" to listOf("首页", "热点", "游戏库", "我"),
    )

    fun locked(title: String): Boolean =
        title == "首页" || title == "主页" || title == "我" || title == "我的"
                || title == "微信" || title == "消息"

    /** Candidate list = stored order first, then defaults, deduped. */
    fun candidates(key: String, cfg: Config.TabLayout): List<String> {
        val out = ArrayList<String>()
        for (t in cfg.order + (DEFAULTS[key] ?: emptyList())) if (t !in out) out.add(t)
        return out
    }

    fun toCfg(order: List<String>, hidden: Set<String>): Config.TabLayout =
        Config.TabLayout(order = order.filter { it !in hidden }, hidden = hidden.toList())
}

private val LOCKED_TITLES = setOf("首页", "主页", "我", "我的", "微信", "消息")

@Composable
fun TabLayoutDialog(
    appKey: String,
    show: Boolean,
    onDismiss: () -> Unit,
    onApply: (Config.TabLayout) -> Unit,
) {
    if (!show) return
    val initial = Config.tabLayout(appKey)
    // entries: (title, hidden) in working order.
    val start = TabLayoutEditor.candidates(appKey, initial).map { t ->
        t to (t in initial.hidden)
    }
    var items by remember(show) { mutableStateOf(start) }
    var dragging by remember(show) { mutableStateOf<String?>(null) }
    var dragDy by remember(show) { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val rowH = with(density) { 48.dp.toPx() }

    OverlayDialog(
        show = show,
        title = "底栏项目",
        summary = "长按拖动排序 · 点眼睛隐藏 · 首个标签与「我」不可移除",
        onDismissRequest = onDismiss,
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { _, (title, hidden) ->
                    val isDragging = dragging == title
                    val baseColor = colorScheme.onBackground
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .alpha(if (hidden && !isDragging) 0.45f else 1f)
                            .then(
                                if (isDragging) Modifier.background(
                                    colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                    RoundedCornerShape(12.dp)
                                ) else Modifier
                            )
                            .graphicsLayer { translationY = if (isDragging) dragDy else 0f }
                            // Key on title: after a swap this row hosts another
                            // item — restart the gesture with fresh captures.
                            .pointerInput(title) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragging = title; dragDy = 0f },
                                    onDragCancel = { dragging = null; dragDy = 0f },
                                    onDragEnd = { dragging = null; dragDy = 0f },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragDy += amount.y
                                        // Stepped swap: crossing one row height
                                        // moves the item and re-anchors the drag.
                                        while (kotlin.math.abs(dragDy) >= rowH) {
                                            val step = if (dragDy > 0) 1 else -1
                                            val cur = items.indexOfFirst { it.first == title }
                                            val target = cur + step
                                            if (target in items.indices && cur >= 0) {
                                                val next = items.toMutableList()
                                                val tmp = next[cur]
                                                next[cur] = next[target]
                                                next[target] = tmp
                                                items = next
                                                dragDy -= step * rowH
                                            } else {
                                                dragDy = 0f
                                                break
                                            }
                                        }
                                    },
                                )
                            },
                    ) {
                        Spacer(Modifier.width(14.dp))
                        Icon(
                            Icons.Filled.DragHandle, contentDescription = "拖动",
                            tint = baseColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            title, fontSize = 16.sp, color = baseColor,
                            modifier = Modifier.weight(1f),
                        )
                        if (TabLayoutEditor.locked(title)) {
                            Icon(
                                Icons.Filled.Lock, contentDescription = "基础项目",
                                tint = baseColor.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(end = 14.dp),
                            )
                        } else {
                            Icon(
                                if (hidden) Icons.Filled.Add else Icons.Filled.Clear,
                                contentDescription = if (hidden) "恢复" else "隐藏",
                                tint = baseColor.copy(alpha = 0.75f),
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable {
                                        items = items.map { (t, h) ->
                                            if (t == title) t to !h else t to h
                                        }
                                    }
                                    .padding(end = 14.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    TextButton(
                        text = "保存",
                        onClick = {
                            val order = items.map { it.first }
                            val hidden = items.filter { it.second }.map { it.first }.toSet()
                            onApply(TabLayoutEditor.toCfg(order, hidden))
                            onDismiss()
                        },
                    )
                }
            }
        },
    )
}
