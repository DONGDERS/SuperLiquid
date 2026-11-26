package io.github.liuran001.mmliquidglass.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import io.github.liuran001.mmliquidglass.GlassConfig.DEFAULT_KUGOU_PLAYER_LIFT_DP

/**
 * Config store for the manager.
 *
 * Every hook parameter is written twice:
 *  - the LSPosed RemotePreferences ("superliquid"), read by hooked hosts via
 *    XposedModule.getRemotePreferences;
 *  - a plain SharedPreferences file of the same name, read by hosts through
 *    XSharedPreferences when the remote channel is unavailable.
 *
 * Keys are shared with GlassConfig (hook side) — keep both in sync.
 */
object Config {

    data class AppCfg(
        val enabled: Boolean = true,
        val widthScale: Float = 1.0f,
        val offsetDp: Int = 12,
        val offsetXdp: Int = 0,
        val blur: Int = 3,
        val tint: Float = 1.0f,
        val pillScale: Float = 1.0f,
        val saturation: Float = 1.0f,
        val gloss: Float = 1.0f,
        val border: Float = 1.0f,
    )

    data class BiliCfg(
        val base: AppCfg = AppCfg(widthScale = 0.7f),
        val plus: PlusCfg = PlusCfg(),
    )

    data class PlusCfg(
        val scale: Float = 1f,
        val bottomDp: Int = 12,
        val sideDp: Int = 16,
    )

    /**
     * Display order + hidden keys bound to a runtime catalog of an installed app.
     * Empty order uses the native order; a stale catalog never applies to a host.
     */
    data class TabLayout(
        val order: List<String> = emptyList(),
        val hidden: List<String> = emptyList(),
        val catalogToken: String = "",
        val labels: Map<String, String> = emptyMap(),
    ) {
        val isDefault: Boolean get() = order.isEmpty() && hidden.isEmpty() && labels.isEmpty()
    }

    const val REMOTE_NAME = "superliquid"

    lateinit var uiPrefs: SharedPreferences
    private var hookPrefs: SharedPreferences? = null

    @Volatile
    private var remotePrefs: SharedPreferences? = null

    private val _uiMode = mutableStateOf(UiMode.DEFAULT)
    val uiMode: String get() = _uiMode.value
    val uiModeEnum: UiMode get() = UiMode.fromValue(_uiMode.value)

    var appParamsCollapsed: Boolean
        get() = uiPrefs.getBoolean("apps_params_collapsed", false)
        set(v) {
            uiPrefs.edit().putBoolean("apps_params_collapsed", v).apply()
            appParamsCollapsedState.value = v
        }
    val appParamsCollapsedState by lazy { mutableStateOf(appParamsCollapsedField()) }
    private fun appParamsCollapsedField(): Boolean =
        runCatching { uiPrefs.getBoolean("apps_params_collapsed", false) }.getOrDefault(false)

    private val _colorMode = mutableStateOf(0) // 0 system / 1 light / 2 dark
    val colorMode: Int get() = _colorMode.value

    private val _serviceConnected = mutableStateOf(false)
    val serviceConnected: Boolean get() = _serviceConnected.value

    internal var frameworkService by mutableStateOf<XposedService?>(null)
        private set

    private val _mm = mutableStateOf(AppCfg())
    val mm: AppCfg get() = _mm.value

    private val _qq = mutableStateOf(AppCfg())
    val qq: AppCfg get() = _qq.value

    private val _bili = mutableStateOf(BiliCfg())
    val bili: BiliCfg get() = _bili.value

    private val _kugou = mutableStateOf(AppCfg())
    val kugou: AppCfg get() = _kugou.value
    private val _pinduoduo = mutableStateOf(AppCfg())
    val pinduoduo: AppCfg get() = _pinduoduo.value
    private val _taobao = mutableStateOf(AppCfg())
    private val _alipay = mutableStateOf(AppCfg())
    val taobao: AppCfg get() = _taobao.value
    val alipay: AppCfg get() = _alipay.value
    private val _kugouPlayerLiftDp = mutableStateOf(DEFAULT_KUGOU_PLAYER_LIFT_DP)
    val kugouPlayerLiftDp: Int get() = _kugouPlayerLiftDp.value
    private val _heybox = mutableStateOf(AppCfg(widthScale = 0.5f))
    val heybox: AppCfg get() = _heybox.value

    private val _heyboxPlus = mutableStateOf(PlusCfg())
    val heyboxPlus: PlusCfg get() = _heyboxPlus.value

    private val _tabs = mutableStateOf(mapOf(
        "mm" to TabLayout(), "qq" to TabLayout(),
        "bili" to TabLayout(), "heybox" to TabLayout(),
        "kugou" to TabLayout(),
        "pinduoduo" to TabLayout(),
        "alipay" to TabLayout(), "taobao" to TabLayout(),
    ))
    fun tabLayout(key: String): TabLayout = _tabs.value[key] ?: TabLayout()

    fun setTabLayout(key: String, t: TabLayout) {
        val snapshot = RuntimeTabs.snapshot(key)
        if (!t.isDefault && (!snapshot.current || t.catalogToken != snapshot.token)) return
        val byKey = snapshot.items.associateBy { it.key }
        val bound = t.copy(
            order = t.order.filter { it in byKey }.distinct(),
            hidden = t.hidden.filter { byKey[it]?.locked == false }.distinct(),
            labels = t.labels.filter { (id, label) -> byKey[id]?.candidates?.contains(label) == true },
        )
        _tabs.value = _tabs.value + (key to bound)
        val json = org.json.JSONArray(bound.order).toString()
        val jsonH = org.json.JSONArray(bound.hidden).toString()
        val jsonLabels = org.json.JSONObject(bound.labels).toString()
        val editor = (remotePrefs ?: hookPrefs)?.edit() ?: return
        editor.putString("${key}_tab_order", json)
        editor.putString("${key}_tab_hidden", jsonH)
        editor.putString("${key}_tab_catalog", bound.catalogToken)
        editor.putString("${key}_tab_labels", jsonLabels)
        editor.apply()
        val mirror = if (remotePrefs != null) hookPrefs else null
        mirror?.edit()?.apply {
            putString("${key}_tab_order", json)
            putString("${key}_tab_hidden", jsonH)
            putString("${key}_tab_catalog", bound.catalogToken)
            putString("${key}_tab_labels", jsonLabels)
        }?.apply()
    }

    fun init(ctx: Context) {
        RuntimeTabs.init(ctx)
        uiPrefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        hookPrefs = ctx.getSharedPreferences(REMOTE_NAME, Context.MODE_PRIVATE)
        _uiMode.value = uiPrefs.getString("ui_mode", UiMode.DEFAULT) ?: UiMode.DEFAULT
        if (uiModeEnum == UiMode.Material) {
            uiPrefs.edit().putBoolean("enable_global_shader_bg", false).apply()
        }
        _colorMode.value = uiPrefs.getInt("color_mode", 0)
        hookPrefs?.let { readHook(it) }

        try {
            XposedServiceHelper.registerListener(object :
                XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    remotePrefs = try {
                        service.getRemotePreferences(REMOTE_NAME)
                    } catch (t: Throwable) {
                        null
                    }
                    _serviceConnected.value = remotePrefs != null
                    remotePrefs?.let { readHook(it) }
                    frameworkService = service
                }

                override fun onServiceDied(service: XposedService) {
                    if (frameworkService !== service) return
                    frameworkService = null
                    remotePrefs = null
                    _serviceConnected.value = false
                }
            })
        } catch (_: Throwable) {
            // LSPosed service unavailable (not installed / too old): the
            // plain-preferences channel below still works through XSP.
        }
    }

    fun setUiMode(v: String) {
        io.github.liuran001.mmliquidglass.data.repository.SettingsRepositoryImpl().uiMode = v
        _uiMode.value = v
        AppSettingsStore.reload()
    }

    fun setColorMode(v: Int) {
        _colorMode.value = v
        uiPrefs.edit().putInt("color_mode", v).apply()
        AppSettingsStore.reload()
    }

    // ------------------------------------------------------------ hook params

    fun setApp(key: String, cfg: AppCfg) {
        when (key) {
            "mm" -> _mm.value = cfg
            "qq" -> _qq.value = cfg
            "heybox" -> _heybox.value = cfg
            "kugou" -> _kugou.value = cfg
            "pinduoduo" -> _pinduoduo.value = cfg
            "taobao" -> _taobao.value = cfg
            "alipay" -> _alipay.value = cfg
            "bili" -> _bili.value = _bili.value.copy(base = cfg)
        }
        write(key, cfg)
    }

    fun setBili(cfg: BiliCfg) {
        _bili.value = cfg
        write("bili", cfg.base)
        writePlus("bili", cfg.plus)
    }

    fun setKugouPlayerLiftDp(value: Int) {
        val dp = value.coerceIn(0, 48)
        _kugouPlayerLiftDp.value = dp
        val remote = remotePrefs
        remote?.edit()?.putInt("kugou_player_lift_dp", dp)?.apply()
        hookPrefs?.edit()?.putInt("kugou_player_lift_dp", dp)?.apply()
    }

    fun setHeyboxPlus(cfg: PlusCfg) {
        _heyboxPlus.value = cfg
        writePlus("heybox", cfg)
    }

    /**
     * Back to built-in defaults. The tab layout is part of what the settings
     * copy promises ("clear all tab-bar glass parameters"), and an empty order /
     * hidden set is exactly the native order — leaving them behind made the
     * button look half-broken.
     */
    fun resetDefaults() {
        setApp("mm", AppCfg())
        setApp("qq", AppCfg())
        setBili(BiliCfg())
        setApp("heybox", AppCfg(widthScale = 0.5f))
        setHeyboxPlus(PlusCfg())
        setApp("kugou", AppCfg())
        setApp("pinduoduo", AppCfg())
        setApp("taobao", AppCfg())
        setApp("alipay", AppCfg())
        setKugouPlayerLiftDp(DEFAULT_KUGOU_PLAYER_LIFT_DP)
        for (key in listOf("mm", "qq", "bili", "heybox", "kugou", "pinduoduo", "taobao", "alipay")) {
            setTabLayout(key, TabLayout())
        }
    }

    private fun write(key: String, cfg: AppCfg) {
        val editor = (remotePrefs ?: hookPrefs)?.edit() ?: return
        fun android.content.SharedPreferences.Editor.putApp(k: String, c: AppCfg) {
            putBoolean("${k}_enabled", c.enabled)
            putFloat("${k}_width_scale", c.widthScale)
            putInt("${k}_offset_dp", c.offsetDp)
            putInt("${k}_offsetx_dp", c.offsetXdp)
            putInt("${k}_blur", c.blur)
            putFloat("${k}_tint", c.tint)
            putFloat("${k}_pill_scale", c.pillScale)
            putFloat("${k}_saturation", c.saturation)
            putFloat("${k}_gloss", c.gloss)
            putFloat("${k}_border", c.border)
        }
        editor.putApp(key, cfg)
        editor.apply()
        // Mirror into the other store so both channels always agree.
        val mirror = if (remotePrefs != null) hookPrefs else null
        mirror?.edit()?.apply { putApp(key, cfg) }?.apply()
    }

    private fun writePlus(key: String, cfg: PlusCfg) {
        val editor = (remotePrefs ?: hookPrefs)?.edit() ?: return
        fun SharedPreferences.Editor.putPlus() = apply {
            putFloat("${key}_pub_scale", cfg.scale)
            putInt("${key}_pub_bottom_dp", cfg.bottomDp)
            putInt("${key}_pub_side_dp", cfg.sideDp)
        }
        editor.putPlus().apply()
        val mirror = if (remotePrefs != null) hookPrefs else null
        mirror?.edit()?.putPlus()?.apply()
    }

    private fun readHook(p: SharedPreferences) {
        fun app(key: String, def: AppCfg): AppCfg = AppCfg(
            // No per-app enable switch any more (LSPosed gates the module):
            // ignore legacy stored values so an old "off" can't stick.
            enabled = true,
            widthScale = p.getFloat("${key}_width_scale", def.widthScale),
            offsetDp = p.getInt("${key}_offset_dp", def.offsetDp),
            offsetXdp = p.getInt("${key}_offsetx_dp", def.offsetXdp),
            blur = p.getInt("${key}_blur", def.blur),
            tint = p.getFloat("${key}_tint", def.tint),
            pillScale = p.getFloat("${key}_pill_scale", def.pillScale),
            saturation = p.getFloat("${key}_saturation", def.saturation),
            gloss = p.getFloat("${key}_gloss", def.gloss),
            border = p.getFloat("${key}_border", def.border),
        )
        _mm.value = app("mm", AppCfg())
        _qq.value = app("qq", AppCfg())
        _heybox.value = app("heybox", AppCfg(widthScale = 0.5f))
        _kugou.value = app("kugou", AppCfg())
        _pinduoduo.value = app("pinduoduo", AppCfg())
        _taobao.value = app("taobao", AppCfg())
        _alipay.value = app("alipay", AppCfg())
        _kugouPlayerLiftDp.value = p.getInt("kugou_player_lift_dp", DEFAULT_KUGOU_PLAYER_LIFT_DP).coerceIn(0, 48)
        _heyboxPlus.value = readPlus(p, "heybox")
        _tabs.value = mapOf(
            "mm" to readTabLayout(p, "mm"), "qq" to readTabLayout(p, "qq"),
            "bili" to readTabLayout(p, "bili"), "heybox" to readTabLayout(p, "heybox"),
            "kugou" to readTabLayout(p, "kugou"),
            "pinduoduo" to readTabLayout(p, "pinduoduo"),
            "alipay" to readTabLayout(p, "alipay"), "taobao" to readTabLayout(p, "taobao"),
        )
        _bili.value = BiliCfg(
            base = app("bili", AppCfg(widthScale = 0.7f)),
            plus = readPlus(p, "bili"),
        )
    }

    private fun readPlus(p: SharedPreferences, key: String, defaults: PlusCfg = PlusCfg()) = PlusCfg(
        scale = p.getFloat("${key}_pub_scale", defaults.scale),
        bottomDp = p.getInt("${key}_pub_bottom_dp", defaults.bottomDp),
        sideDp = p.getInt("${key}_pub_side_dp", defaults.sideDp),
    )

    /** Tolerant <key>_tab_* read: corrupt JSON → default (native order). */
    private fun readTabLayout(p: SharedPreferences, key: String): TabLayout {
        fun arr(name: String): List<String> = runCatching {
            val s = p.getString(name, null) ?: return emptyList()
            if (s.isEmpty()) return emptyList()
            val a = org.json.JSONArray(s)
            (0 until a.length()).map { a.getString(it) }
        }.getOrDefault(emptyList())
        return TabLayout(order = arr("${key}_tab_order"), hidden = arr("${key}_tab_hidden"),
            catalogToken = p.getString("${key}_tab_catalog", "").orEmpty(),
            labels = runCatching {
                val json = org.json.JSONObject(p.getString("${key}_tab_labels", "{}") ?: "{}")
                json.keys().asSequence().associateWith { json.getString(it) }
            }.getOrDefault(emptyMap()))
    }
}
