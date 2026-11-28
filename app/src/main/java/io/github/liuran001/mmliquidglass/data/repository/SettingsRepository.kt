package io.github.liuran001.mmliquidglass.data.repository

/**
 * Theme-facing slice of KSU's SettingsRepository, backed by the manager's
 * own SharedPreferences ("settings"). Property names match the ported
 * ui/theme files verbatim so they compile untouched.
 */
interface SettingsRepository {
    var uiMode: String
    var themeMode: Int
    var miuixMonet: Boolean
    var keyColor: Int
    var colorStyle: String
    var colorSpec: String
    var enablePredictiveBack: Boolean
    var enableBlur: Boolean
    var enableFloatingBottomBar: Boolean
    var enableFloatingBottomBarBlur: Boolean
    var enableGlobalShaderBg: Boolean
    var enableAppExpandLimit: Boolean
    var appExpandLimit: Int
}

class SettingsRepositoryImpl : SettingsRepository {

    private val p get() = io.github.liuran001.mmliquidglass.ui.Config.uiPrefs

    override var uiMode: String
        get() = p.getString("ui_mode", "miuix") ?: "miuix"
        set(value) {
            p.edit().apply {
                putString("ui_mode", value)
                if (value == "material") putBoolean("enable_global_shader_bg", false)
            }.apply()
        }

    override var themeMode: Int
        get() = p.getInt("color_mode", 0)
        set(value) { p.edit().putInt("color_mode", value).apply() }

    override var miuixMonet: Boolean
        get() = p.getBoolean("miuix_monet", false)
        set(value) { p.edit().putBoolean("miuix_monet", value).apply() }

    override var keyColor: Int
        get() = p.getInt("key_color", 0xFF3482FF.toInt())
        set(value) { p.edit().putInt("key_color", value).apply() }

    override var colorStyle: String
        get() = p.getString("color_style", "TonalSpot") ?: "TonalSpot"
        set(value) { p.edit().putString("color_style", value).apply() }

    override var colorSpec: String
        get() = p.getString("color_spec", "SPEC_2025") ?: "SPEC_2025"
        set(value) { p.edit().putString("color_spec", value).apply() }

    override var enablePredictiveBack: Boolean
        get() = p.getBoolean("enable_predictive_back", false)
        set(value) { p.edit().putBoolean("enable_predictive_back", value).apply() }

    override var enableBlur: Boolean
        get() = p.getBoolean("enable_blur", true)
        set(value) { p.edit().putBoolean("enable_blur", value).apply() }

    override var enableFloatingBottomBar: Boolean
        get() = p.getBoolean("enable_floating_bottom_bar", false)
        set(value) { p.edit().putBoolean("enable_floating_bottom_bar", value).apply() }

    override var enableFloatingBottomBarBlur: Boolean
        get() = p.getBoolean("enable_floating_bottom_bar_blur", false)
        set(value) { p.edit().putBoolean("enable_floating_bottom_bar_blur", value).apply() }
    private val shaderKey get() = if (uiMode == "liquid_glass") "liquid_glass_light_effects" else "enable_global_shader_bg"
    override var enableGlobalShaderBg: Boolean
        get() = io.github.liuran001.mmliquidglass.ui.UiMode.fromValue(uiMode).supportsLightEffects && p.getBoolean(shaderKey, true)
        set(value) { p.edit().putBoolean(shaderKey, value && io.github.liuran001.mmliquidglass.ui.UiMode.fromValue(uiMode).supportsLightEffects).apply() }
    override var enableAppExpandLimit: Boolean
        get() = p.getBoolean("enable_app_expand_limit", true)
        set(value) { p.edit().putBoolean("enable_app_expand_limit", value).apply() }
    override var appExpandLimit: Int
        get() = p.getInt("app_expand_limit", 3).coerceIn(1, 10)
        set(value) { p.edit().putInt("app_expand_limit", value.coerceIn(1, 10)).apply() }
}
