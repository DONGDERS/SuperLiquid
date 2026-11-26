package io.github.liuran001.mmliquidglass.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.liuran001.mmliquidglass.TabCatalogProvider
import io.github.liuran001.mmliquidglass.TabCatalogRules
import org.json.JSONObject

/** Manager-local copy of reports sent by the hooked native bars. */
object RuntimeTabs {
    data class Item(
        val key: String, val label: String, val locked: Boolean,
        val nativeIndex: Int = -1, val candidates: List<String> = emptyList(),
    )
    data class Snapshot(
        val items: List<Item> = emptyList(),
        val version: String = "",
        val seenAt: Long = 0,
        val current: Boolean = false,
        val installed: Boolean = true,
        val token: String = "",
        val needsRescan: Boolean = false,
    ) {
        val status: String get() = when {
            !installed -> "应用未安装"
            current -> "已从应用读取 ${items.size} 项 · $version"
            needsRescan -> "底栏记录需重新读取 · 打开应用首页"
            items.isNotEmpty() -> "应用已更新 · 打开应用首页重新读取"
            else -> "尚未读取 · 打开应用首页后自动同步"
        }
    }
    private var snapshots by mutableStateOf<Map<String, Snapshot>>(emptyMap())
    private var context: Context? = null
    private var prefs: SharedPreferences? = null
    private val keys = listOf("mm", "qq", "bili", "heybox", "kugou", "pinduoduo", "taobao", "alipay")
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }
    fun init(ctx: Context) {
        context = ctx.applicationContext
        val next = TabCatalogProvider.preferences(ctx)
        if (prefs !== next) {
            prefs?.unregisterOnSharedPreferenceChangeListener(listener)
            prefs = next
            next.registerOnSharedPreferenceChangeListener(listener)
        }
        refresh()
    }
    fun snapshot(key: String): Snapshot = snapshots[key] ?: Snapshot()
    fun refresh() {
        val ctx = context ?: return
        val stored = prefs ?: return
        snapshots = keys.associateWith { key ->
            val info = runCatching {
                ctx.packageManager.getPackageInfo(TabCatalogRules.packageFor(key)!!, 0)
            }.getOrNull()
            runCatching {
                val json = stored.getString(key, null)?.let(::JSONObject)
                val array = json?.getJSONArray("items")
                val items = if (array == null) emptyList() else (0 until array.length()).map { i ->
                    val item = array.getJSONObject(i)
                    Item(
                        key = item.getString("key"), label = item.getString("label"),
                        locked = item.getBoolean("locked"), nativeIndex = item.optInt("native_index", i),
                        candidates = item.optJSONArray("candidates")?.let { values ->
                            (0 until values.length()).map { index -> values.getString(index) }
                        }.orEmpty(),
                    )
                }
                Snapshot(
                    items = items,
                    version = json?.optString("version_name").orEmpty(),
                    seenAt = json?.optLong("seen_at") ?: 0,
                    current = info != null && json != null && items.size in 2..TabCatalogRules.MAX_TABS &&
                        json.optString("package") == TabCatalogRules.packageFor(key) &&
                        json.optInt("schema") == TabCatalogRules.SCHEMA &&
                        json.optLong("version_code") == info.longVersionCode &&
                        json.optLong("updated_at") == info.lastUpdateTime,
                    installed = info != null,
                    needsRescan = json != null && json.optInt("schema") != TabCatalogRules.SCHEMA,
                    token = if (info != null) TabCatalogRules.catalogToken(
                        info.longVersionCode, info.lastUpdateTime, items.map { it.key },
                    ) else "",
                )
            }.getOrElse { Snapshot(installed = info != null) }
        }
    }
}
