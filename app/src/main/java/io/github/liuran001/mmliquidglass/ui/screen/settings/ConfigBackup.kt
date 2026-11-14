package io.github.liuran001.mmliquidglass.ui.screen.settings

import io.github.liuran001.mmliquidglass.BuildConfig
import io.github.liuran001.mmliquidglass.ui.Config
import org.json.JSONArray
import org.json.JSONObject

/**
 * Config backup: export the full manager state as one JSON file, import it
 * back with per-field tolerance — a bad or missing field keeps the current
 * value instead of failing the whole import (10-10 lesson: one bad entry
 * must never blank the table).
 *
 * Fields must mirror Config's prefs keys (keep in sync with GlassConfig).
 */
object ConfigBackup {

    /** Everything the manager persists, in one document. */
    fun buildJson(): String {
        val root = JSONObject()
        root.put("app", "superliquid")
        root.put("schema", 1)
        root.put("version", BuildConfig.VERSION_NAME)

        fun appCfg(c: Config.AppCfg) = JSONObject().apply {
            put("widthScale", c.widthScale.toDouble())
            put("offsetDp", c.offsetDp)
            put("offsetXdp", c.offsetXdp)
            put("blur", c.blur)
            put("tint", c.tint.toDouble())
            put("pillScale", c.pillScale.toDouble())
            put("saturation", c.saturation.toDouble())
            put("gloss", c.gloss.toDouble())
            put("border", c.border.toDouble())
        }
        root.put("mm", appCfg(Config.mm))
        root.put("qq", appCfg(Config.qq))
        root.put("heybox", appCfg(Config.heybox))
        root.put("bili", appCfg(Config.bili.base).apply {
            put("hoverTint", Config.bili.hoverTint)
            put("hideNative", Config.bili.hideNative)
            put("pubScale", Config.bili.pubScale.toDouble())
            put("pubBottomDp", Config.bili.pubBottomDp)
            put("pubSideDp", Config.bili.pubSideDp)
        })
        root.put("tabLayout", JSONObject().apply {
            for (key in listOf("mm", "qq", "bili", "heybox")) {
                val t = Config.tabLayout(key)
                if (!t.isDefault) put(key, JSONObject().apply {
                    put("order", JSONArray(t.order))
                    put("hidden", JSONArray(t.hidden))
                })
            }
        })
        root.put("ui", JSONObject().apply {
            put("uiMode", Config.uiMode)
            put("colorMode", Config.colorMode)
        })
        return root.toString(2)
    }

    /**
     * Tolerant merge: every field individually guarded; anything unreadable
     * or absent is skipped. Returns the number of applied sections.
     */
    fun applyJson(text: String): Int {
        val root = JSONObject(text) // whole-file garbage → throws → 0 applied
        var applied = 0

        fun app(key: String): Config.AppCfg? {
            val o = root.optJSONObject(key) ?: return null
            val cur = when (key) {
                "mm" -> Config.mm; "qq" -> Config.qq
                "heybox" -> Config.heybox; "bili" -> Config.bili.base
                else -> return null
            }
            var c = cur
            for (f in listOf(
                "widthScale", "tint", "pillScale", "saturation", "gloss", "border"
            )) {
                runCatching { c = setF(c, f, o.getDouble(f).toFloat()) }
            }
            for (f in listOf("offsetDp", "offsetXdp", "blur")) {
                runCatching { c = setI(c, f, o.getInt(f)) }
            }
            return c
        }
        app("mm")?.let { Config.setApp("mm", it); applied++ }
        app("qq")?.let { Config.setApp("qq", it); applied++ }
        app("heybox")?.let { Config.setApp("heybox", it); applied++ }

        runCatching {
            val o = root.getJSONObject("bili")
            val b = Config.bili
            var base = b.base
            for (f in listOf("widthScale", "tint", "pillScale", "saturation", "gloss", "border")) {
                runCatching { base = setF(base, f, o.getDouble(f).toFloat()) }
            }
            for (f in listOf("offsetDp", "offsetXdp", "blur")) {
                runCatching { base = setI(base, f, o.getInt(f)) }
            }
            Config.setBili(Config.BiliCfg(
                base = base,
                hoverTint = o.optBoolean("hoverTint", b.hoverTint),
                hideNative = o.optBoolean("hideNative", b.hideNative),
                pubScale = o.optDouble("pubScale", b.pubScale.toDouble()).toFloat(),
                pubBottomDp = o.optInt("pubBottomDp", b.pubBottomDp),
                pubSideDp = o.optInt("pubSideDp", b.pubSideDp),
            ))
            applied++
        }

        runCatching {
            val t = root.getJSONObject("tabLayout")
            fun arr(o: JSONObject, k: String): List<String> = runCatching {
                val a = o.getJSONArray(k)
                (0 until a.length()).mapNotNull { runCatching { a.getString(it) }.getOrNull() }
            }.getOrDefault(emptyList())
            for (key in listOf("mm", "qq", "bili", "heybox")) {
                runCatching {
                    val o = t.getJSONObject(key)
                    Config.setTabLayout(key, Config.TabLayout(
                        order = arr(o, "order"), hidden = arr(o, "hidden")))
                } // per-app tolerance: a bad entry skips only that app
            }
            applied++
        }

        runCatching {
            val u = root.getJSONObject("ui")
            u.optString("uiMode").takeIf { it.isNotEmpty() }?.let { Config.setUiMode(it) }
            u.optInt("colorMode", Config.colorMode).let { Config.setColorMode(it) }
            applied++
        }
        return applied
    }

    // AppCfg is a data class — set via copy() keyed by field name.
    private fun setF(c: Config.AppCfg, f: String, v: Float) = when (f) {
        "widthScale" -> c.copy(widthScale = v)
        "tint" -> c.copy(tint = v)
        "pillScale" -> c.copy(pillScale = v)
        "saturation" -> c.copy(saturation = v)
        "gloss" -> c.copy(gloss = v)
        "border" -> c.copy(border = v)
        else -> c
    }

    private fun setI(c: Config.AppCfg, f: String, v: Int) = when (f) {
        "offsetDp" -> c.copy(offsetDp = v)
        "offsetXdp" -> c.copy(offsetXdp = v)
        "blur" -> c.copy(blur = v)
        else -> c
    }
}
