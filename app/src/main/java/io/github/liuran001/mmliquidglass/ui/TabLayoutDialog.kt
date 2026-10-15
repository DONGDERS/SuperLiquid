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
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text as M3Text
import androidx.compose.material3.TextButton as M3TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

    /** Known tab titles per app key; unmatched runtime tabs ride at the end.
     *  Verified by TA on the live bars (2026-10-15):
     *  WeChat = 微信/通讯录/发现/我 (first tab IS 微信, not 消息);
     *  QQ = 消息/频道/联系人/动态 (no 我 tab). */
    val DEFAULTS = mapOf(
        "mm" to listOf("微信", "通讯录", "发现", "我"),
        "qq" to listOf("消息", "频道", "联系人", "动态"),
        "bili" to listOf("首页", "动态", "会员购", "我的"),
        "heybox" to listOf("首页", "热点", "游戏库", "我"),
    )

    /** Basics per app: never hideable, never reorderable away from slot 0. */
    val LOCKED = mapOf(
        "mm" to setOf("微信", "我"),
        "qq" to setOf("消息", "联系人"),
        "bili" to setOf("首页", "我的"),
        "heybox" to setOf("首页", "我"),
    )

    fun locked(appKey: String, title: String): Boolean =
        title in (LOCKED[appKey] ?: emptySet())

    /** Candidate list = stored order first, then defaults, deduped. */
    fun candidates(key: String, cfg: Config.TabLayout): List<String> {
        val out = ArrayList<String>()
        // Entry tab (slot 0) is pinned to the front regardless of what an
        // older config stored there.
        DEFAULTS[key]?.firstOrNull()?.let { out.add(it) }
        for (t in cfg.order + (DEFAULTS[key] ?: emptyList())) if (t !in out) out.add(t)
        return out
    }

    fun toCfg(order: List<String>, hidden: Set<String>): Config.TabLayout =
        Config.TabLayout(order = order.filter { it !in hidden }, hidden = hidden.toList())
}

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

    // Shared rows body for both theme shells. First row is PINNED: no drag
    // gesture, can never be pushed below slot 0 (target coerced to >=1).
    @Composable
    fun rowsBody(baseColor: Color, dragBg: Color) {
        Column(modifier = Modifier.fillMaxWidth()) {
            items.forEachIndexed { idx, (title, hidden) ->
                val isDragging = dragging == title
                val dragIndex = dragging?.let { d -> items.indexOfFirst { it.first == d } } ?: -1
                // Target slot from accumulated offset; list mutates only on
                // release. Slot 0 is untouchable: coerce to >= 1.
                val target = if (dragIndex < 0) idx
                    else (dragIndex + kotlin.math.round(dragDy / rowH).toInt())
                        .coerceIn(1, items.lastIndex)
                val shift = when {
                    dragging == null -> 0f
                    isDragging -> dragDy
                    dragIndex < 0 -> 0f
                    idx in (dragIndex + 1)..target -> -rowH
                    idx in target until dragIndex -> rowH
                    else -> 0f
                }
                val isPinned = idx == 0
                val isLockedRow = isPinned || TabLayoutEditor.locked(appKey, title)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .alpha(if (hidden && !isDragging) 0.45f else 1f)
                        .zIndex(if (isDragging) 1f else 0f)
                        // Layer BEFORE the background: modifiers after
                        // graphicsLayer live inside it — if the background
                        // sits outside, it stays put while the row moves.
                        .graphicsLayer { translationY = shift }
                        .then(
                            if (isDragging) Modifier.background(
                                dragBg, RoundedCornerShape(12.dp)
                            ) else Modifier
                        )
                        .then(
                            if (isPinned) Modifier
                            else Modifier
                                // Key on title keeps captures fresh without
                                // restarting mid-gesture (the list never moves
                                // while a drag is live, so this is stable).
                                .pointerInput(title) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { dragging = title; dragDy = 0f },
                                        onDragCancel = { dragging = null; dragDy = 0f },
                                        onDragEnd = {
                                            // Apply the pending move once, here.
                                            val from = items.indexOfFirst { it.first == title }
                                            val to = (from + kotlin.math.round(dragDy / rowH).toInt())
                                                .coerceIn(1, items.lastIndex)
                                            if (from >= 0 && to != from) {
                                                val next = items.toMutableList()
                                                val moved = next.removeAt(from)
                                                next.add(to, moved)
                                                items = next
                                            }
                                            dragging = null; dragDy = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragDy += amount.y
                                        },
                                    )
                                }
                        ),
                ) {
                    Spacer(Modifier.width(14.dp))
                    // Drag affordance as a glyph: DragHandle lives in
                    // material-icons-extended which we don't ship — core's
                    // verified icon set is Add/Clear/Lock only.
                    Text(
                        "≡", fontSize = 22.sp,
                        color = baseColor.copy(alpha = if (isPinned) 0.2f else 0.6f),
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        title, fontSize = 16.sp, color = baseColor,
                        modifier = Modifier.weight(1f),
                    )
                    if (isLockedRow) {
                        Icon(
                            Icons.Filled.Lock, contentDescription = "基础项目",
                            tint = baseColor.copy(alpha = 0.45f),
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 10.dp),
                        )
                    } else {
                        Icon(
                            if (hidden) Icons.Filled.Add else Icons.Filled.Clear,
                            contentDescription = if (hidden) "恢复" else "隐藏",
                            tint = baseColor.copy(alpha = 0.75f),
                            modifier = Modifier
                                .size(28.dp)
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
        }
    }

    fun applyEdit() {
        val order = items.map { it.first }
        val hidden = items.filter { it.second }.map { it.first }.toSet()
        onApply(TabLayoutEditor.toCfg(order, hidden))
        onDismiss()
    }

    val SUMMARY = "长按拖动排序 · 点眼睛隐藏 · 首项固定不可移除"
    when (LocalUiMode.current) {
        UiMode.Miuix -> OverlayDialog(
            show = show,
            title = "底栏项目",
            summary = SUMMARY,
            onDismissRequest = onDismiss,
            content = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    rowsBody(colorScheme.onBackground, colorScheme.secondaryContainer.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            text = "取消",
                            onClick = onDismiss,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        TextButton(text = "保存", onClick = { applyEdit() })
                    }
                }
            },
        )
        UiMode.Material -> if (show) Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 56.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    M3Text(
                        "底栏项目",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    M3Text(
                        SUMMARY,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    rowsBody(
                        MaterialTheme.colorScheme.onSurface,
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        M3TextButton(onClick = onDismiss) { M3Text("取消") }
                        Spacer(Modifier.width(12.dp))
                        M3TextButton(onClick = { applyEdit() }) { M3Text("保存") }
                    }
                }
            }
        }
    }
}
