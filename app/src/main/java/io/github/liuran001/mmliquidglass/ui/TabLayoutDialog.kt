package io.github.liuran001.mmliquidglass.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.key
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

/** Edits the latest native report. Unknown/stale bars are never guessed. */
object TabLayoutEditor {
    fun locked(appKey: String, title: String): Boolean =
        RuntimeTabs.snapshot(appKey).items.firstOrNull { it.key == title }?.locked == true
    fun candidates(key: String, cfg: Config.TabLayout): List<String> {
        val snapshot = RuntimeTabs.snapshot(key)
        if (!snapshot.current) return emptyList()
        val native = snapshot.items.map { it.key }
        val saved = if (cfg.catalogToken == snapshot.token) cfg.order else emptyList()
        return io.github.liuran001.mmliquidglass.TabCatalogRules.candidateOrder(native, saved)
    }
}

@Composable
fun TabLayoutDialog(
    appKey: String,
    show: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onApply: (Config.TabLayout) -> Unit,
) {
    val snapshot = RuntimeTabs.snapshot(appKey)
    val initial = Config.tabLayout(appKey).takeIf { it.catalogToken == snapshot.token }
        ?: Config.TabLayout()
    val hiddenKeys = initial.hidden.toSet()
    var showAll by remember(appKey, snapshot.token) { mutableStateOf(false) }
    val dismiss = { if (showAll && show) showAll = false else onDismiss() }
    val start = TabLayoutEditor.candidates(appKey, initial).map { key ->
        key to (key in hiddenKeys && !TabLayoutEditor.locked(appKey, key))
    }
    // A new report replaces an obsolete editor draft instead of saving stale rows.
    var items by remember(appKey, snapshot.current, snapshot.token) { mutableStateOf(start) }
    val pinned = items.firstOrNull()?.first

    fun setIncluded(id: String, included: Boolean) {
        if (!show || id == pinned || TabLayoutEditor.locked(appKey, id)) return
        items = items.map { (key, hidden) -> if (key == id) key to !included else key to hidden }
    }

    @Composable
    fun candidateBody(color: Color) {
        val optional = snapshot.items.filter { !it.locked && it.key != pinned }
        Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            if (optional.isEmpty()) Text("没有可选项目", color = color)
            optional.forEach { item -> key(item.key) {
                val included = items.any { it.first == item.key && !it.second }
                Row(
                    Modifier.fillMaxWidth().toggleable(
                        value = included, enabled = show, role = Role.Checkbox,
                        onValueChange = { setIncluded(item.key, it) },
                    ).padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = included, onCheckedChange = null)
                    Spacer(Modifier.width(12.dp))
                    Text(item.label, color = color, modifier = Modifier.weight(1f))
                }
            } }
        }
    }
    var dragging by remember(appKey, snapshot.current, snapshot.token) { mutableStateOf<String?>(null) }
    var dragDy by remember(appKey, snapshot.current, snapshot.token) { mutableFloatStateOf(0f) }
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
                        snapshot.items.firstOrNull { it.key == title }?.label ?: title,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontSize = 16.sp, color = baseColor,
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
        if (!snapshot.current || items.isEmpty()) return
        val order = items.map { it.first }
        val hidden = items.filter { it.second }.map { it.first }.toSet()
        onApply(Config.TabLayout(order = order, hidden = hidden.toList(),
            catalogToken = snapshot.token))
        onDismiss()
    }

    val title = if (showAll) "选择底栏项目" else "底栏项目"
    val summary = snapshot.status
    @Composable
    fun body(color: Color, dragBg: Color) {
        if (showAll) candidateBody(color)
        else Column(Modifier.heightIn(max = 384.dp).verticalScroll(rememberScrollState())) {
            rowsBody(color, dragBg)
        }
    }
    when (LocalUiMode.current) {
        UiMode.Miuix -> OverlayDialog(
            show = show,
            title = title,
            summary = summary,
            onDismissRequest = dismiss,
            onDismissFinished = onDismissFinished,
        ) {
            body(colorScheme.onBackground, colorScheme.secondaryContainer.copy(alpha = 0.4f))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                TextButton(text = if (showAll) "返回" else "取消", onClick = dismiss)
                if (snapshot.current && !showAll) TextButton(text = "保存", onClick = { if (show) applyEdit() })
            }
        }
        UiMode.Material -> {
            val transition = remember { MutableTransitionState(false) }
            transition.targetState = show
            LaunchedEffect(show, transition.isIdle, transition.currentState) {
                if (!show && transition.isIdle && !transition.currentState) onDismissFinished()
            }
            Dialog(
                onDismissRequest = dismiss,
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                AnimatedVisibility(
                    visibleState = transition,
                    enter = fadeIn() + scaleIn(initialScale = 0.94f),
                    exit = fadeOut() + scaleOut(targetScale = 0.94f),
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(20.dp)) {
                            M3Text(title, style = MaterialTheme.typography.titleLarge)
                            M3Text(summary, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp))
                            body(MaterialTheme.colorScheme.onSurface,
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                            Row(Modifier.fillMaxWidth()) {
                                Spacer(Modifier.weight(1f))
                                M3TextButton(onClick = dismiss, enabled = show) { M3Text(if (showAll) "返回" else "取消") }
                                if (snapshot.current && !showAll) M3TextButton(onClick = { applyEdit() }, enabled = show) {
                                    M3Text("保存")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
