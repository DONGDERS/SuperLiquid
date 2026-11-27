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
        root.put("railway", appCfg(Config.railway).apply {
            put("noticeLiftDp", Config.railwayNoticeLiftDp)
        })
        root.put("kugou", appCfg(Config.kugou).apply {
            put("playerLiftDp", Config.kugouPlayerLiftDp)
        })
        root.put("heybox", appCfg(Config.heybox).apply {
            writePlus(Config.heyboxPlus)
        })
        root.put("bili", appCfg(Config.bili.base).apply {
            writePlus(Config.bili.plus)
        })
        root.put("tabLayout", JSONObject().apply {
            for (key in listOf("mm", "qq", "bili", "heybox", "kugou")) {
                val t = Config.tabLayout(key)
                if (!t.isDefault) put(key, JSONObject().apply {
                    put("order", JSONArray(t.order))
                    put("hidden", JSONArray(t.hidden))
                    put("catalogToken", t.catalogToken)
                    put("labels", JSONObject(t.labels))
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
                "kugou" -> Config.kugou
                "railway" -> Config.railway
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
        app("railway")?.let { cfg ->
            Config.setApp("railway", cfg)
            runCatching { root.getJSONObject("railway").getInt("noticeLiftDp") }
                .getOrNull()?.let(Config::setRailwayNoticeLiftDp)
            applied++
        }
        app("kugou")?.let { cfg ->
            Config.setApp("kugou", cfg)
            runCatching { root.getJSONObject("kugou").getInt("playerLiftDp") }
                .getOrNull()?.let(Config::setKugouPlayerLiftDp)
            applied++
        }
        app("heybox")?.let {
            Config.setApp("heybox", it)
            Config.setHeyboxPlus(root.getJSONObject("heybox").readPlus(Config.heyboxPlus))
            applied++
        }

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
                plus = o.readPlus(b.plus),
            ))
            applied++
        }

        runCatching {
            val t = root.getJSONObject("tabLayout")
            fun arr(o: JSONObject, k: String): List<String> = runCatching {
                val a = o.getJSONArray(k)
                (0 until a.length()).mapNotNull { runCatching { a.getString(it) }.getOrNull() }
            }.getOrDefault(emptyList())
            for (key in listOf("mm", "qq", "bili", "heybox", "kugou")) {
                runCatching {
                    val o = t.getJSONObject(key)
                    val token = o.optString("catalogToken")
                    if (token.isNotEmpty()) {
                        val labels = o.optJSONObject("labels")?.let { values ->
                            values.keys().asSequence().associateWith { values.getString(it) }
                        }.orEmpty()
                        Config.setTabLayout(key, Config.TabLayout(
                            order = arr(o, "order"), hidden = arr(o, "hidden"),
                            catalogToken = token, labels = labels))
                    }
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

    private fun JSONObject.writePlus(plus: Config.PlusCfg) {
        put("pubScale", plus.scale.toDouble())
        put("pubBottomDp", plus.bottomDp)
        put("pubSideDp", plus.sideDp)
    }

    private fun JSONObject.readPlus(defaults: Config.PlusCfg) = Config.PlusCfg(
        scale = optDouble("pubScale", defaults.scale.toDouble()).takeIf { it.isFinite() }
            ?.coerceIn(0.5, 2.0)?.toFloat() ?: defaults.scale,
        bottomDp = optInt("pubBottomDp", defaults.bottomDp).coerceIn(0, 48),
        sideDp = optInt("pubSideDp", defaults.sideDp).coerceIn(0, 64),
    )

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
