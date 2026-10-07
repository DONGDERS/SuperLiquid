package io.github.liuran001.mmliquidglass.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

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
        val blur: Int = 3,
        val tint: Float = 1.0f,
    )

    data class BiliCfg(
        val base: AppCfg = AppCfg(widthScale = 0.7f),
        val hoverTint: Boolean = true,
        val hideNative: Boolean = true,
        val pubScale: Float = 1.0f,
        val pubBottomDp: Int = 12,
        val pubSideDp: Int = 16,
    )

    const val REMOTE_NAME = "superliquid"

    lateinit var uiPrefs: SharedPreferences
    private var hookPrefs: SharedPreferences? = null

    @Volatile
    private var remotePrefs: SharedPreferences? = null

    private val _uiMode = mutableStateOf(UiMode.DEFAULT)
    val uiMode: String get() = _uiMode.value
    val uiModeEnum: UiMode get() = UiMode.fromValue(_uiMode.value)

    private val _colorMode = mutableStateOf(0) // 0 system / 1 light / 2 dark
    val colorMode: Int get() = _colorMode.value

    private val _serviceConnected = mutableStateOf(false)
    val serviceConnected: Boolean get() = _serviceConnected.value

    private val _mm = mutableStateOf(AppCfg())
    val mm: AppCfg get() = _mm.value

    private val _qq = mutableStateOf(AppCfg())
    val qq: AppCfg get() = _qq.value

    private val _bili = mutableStateOf(BiliCfg())
    val bili: BiliCfg get() = _bili.value

    fun init(ctx: Context) {
        uiPrefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        hookPrefs = ctx.getSharedPreferences(REMOTE_NAME, Context.MODE_PRIVATE)
        _uiMode.value = uiPrefs.getString("ui_mode", UiMode.DEFAULT) ?: UiMode.DEFAULT
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
                }

                override fun onServiceDied(service: XposedService) {
                    _serviceConnected.value = false
                }
            })
        } catch (_: Throwable) {
            // LSPosed service unavailable (not installed / too old): the
            // plain-preferences channel below still works through XSP.
        }
    }

    fun setUiMode(v: String) {
        _uiMode.value = v
        uiPrefs.edit().putString("ui_mode", v).apply()
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
            "bili" -> _bili.value = _bili.value.copy(base = cfg)
        }
        write(key, cfg)
    }

    fun setBili(cfg: BiliCfg) {
        _bili.value = cfg
        write("bili", cfg.base)
        writeBiliExtras(cfg)
    }

    fun resetDefaults() {
        setApp("mm", AppCfg())
        setApp("qq", AppCfg())
        setBili(BiliCfg())
    }

    private fun write(key: String, cfg: AppCfg) {
        val editor = (remotePrefs ?: hookPrefs)?.edit() ?: return
        editor.putBoolean("${key}_enabled", cfg.enabled)
        editor.putFloat("${key}_width_scale", cfg.widthScale)
        editor.putInt("${key}_offset_dp", cfg.offsetDp)
        editor.putInt("${key}_blur", cfg.blur)
        editor.putFloat("${key}_tint", cfg.tint)
        editor.apply()
        // Mirror into the other store so both channels always agree.
        val mirror = if (remotePrefs != null) hookPrefs else null
        mirror?.edit()?.apply {
            putBoolean("${key}_enabled", cfg.enabled)
            putFloat("${key}_width_scale", cfg.widthScale)
            putInt("${key}_offset_dp", cfg.offsetDp)
            putInt("${key}_blur", cfg.blur)
            putFloat("${key}_tint", cfg.tint)
        }?.apply()
    }

    private fun writeBiliExtras(cfg: BiliCfg) {
        val editor = (remotePrefs ?: hookPrefs)?.edit() ?: return
        editor.putBoolean("bili_hover_tint", cfg.hoverTint)
        editor.putBoolean("bili_hide_native", cfg.hideNative)
        editor.putFloat("bili_pub_scale", cfg.pubScale)
        editor.putInt("bili_pub_bottom_dp", cfg.pubBottomDp)
        editor.putInt("bili_pub_side_dp", cfg.pubSideDp)
        editor.apply()
        val mirror = if (remotePrefs != null) hookPrefs else null
        mirror?.edit()?.apply {
            putBoolean("bili_hover_tint", cfg.hoverTint)
            putBoolean("bili_hide_native", cfg.hideNative)
            putFloat("bili_pub_scale", cfg.pubScale)
            putInt("bili_pub_bottom_dp", cfg.pubBottomDp)
            putInt("bili_pub_side_dp", cfg.pubSideDp)
        }?.apply()
    }

    private fun readHook(p: SharedPreferences) {
        fun app(key: String, def: AppCfg): AppCfg = AppCfg(
            enabled = p.getBoolean("${key}_enabled", def.enabled),
            widthScale = p.getFloat("${key}_width_scale", def.widthScale),
            offsetDp = p.getInt("${key}_offset_dp", def.offsetDp),
            blur = p.getInt("${key}_blur", def.blur),
            tint = p.getFloat("${key}_tint", def.tint),
        )
        _mm.value = app("mm", AppCfg())
        _qq.value = app("qq", AppCfg())
        val b = _bili.value
        _bili.value = BiliCfg(
            base = app("bili", AppCfg(widthScale = 0.7f)),
            hoverTint = p.getBoolean("bili_hover_tint", b.hoverTint),
            hideNative = p.getBoolean("bili_hide_native", b.hideNative),
            pubScale = p.getFloat("bili_pub_scale", b.pubScale),
            pubBottomDp = p.getInt("bili_pub_bottom_dp", b.pubBottomDp),
            pubSideDp = p.getInt("bili_pub_side_dp", b.pubSideDp),
        )
    }
}
