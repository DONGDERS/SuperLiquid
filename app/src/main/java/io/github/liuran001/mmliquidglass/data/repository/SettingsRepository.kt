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
    var enableSwipeDismiss: Boolean
    var pagerInterceptionMode: Int
    var enableBlur: Boolean
    var enableFloatingBottomBar: Boolean
    var enableFloatingBottomBarBlur: Boolean
    var enableNavigationBadge: Boolean
    var pageScale: Float
    var moduleDescriptionMaxLines: Int
}

class SettingsRepositoryImpl : SettingsRepository {

    private val p get() = io.github.liuran001.mmliquidglass.ui.Config.uiPrefs

    override var uiMode: String
        get() = p.getString("ui_mode", "mat") ?: "mat"
        set(value) { p.edit().putString("ui_mode", value).apply() }

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

    override var enableSwipeDismiss: Boolean
        get() = p.getBoolean("enable_swipe_dismiss", true)
        set(value) { p.edit().putBoolean("enable_swipe_dismiss", value).apply() }

    override var pagerInterceptionMode: Int
        get() = p.getInt("pager_interception_mode", 1)
        set(value) { p.edit().putInt("pager_interception_mode", value).apply() }

    override var enableBlur: Boolean
        get() = p.getBoolean("enable_blur", true)
        set(value) { p.edit().putBoolean("enable_blur", value).apply() }

    override var enableFloatingBottomBar: Boolean
        get() = p.getBoolean("enable_floating_bottom_bar", false)
        set(value) { p.edit().putBoolean("enable_floating_bottom_bar", value).apply() }

    override var enableFloatingBottomBarBlur: Boolean
        get() = p.getBoolean("enable_floating_bottom_bar_blur", false)
        set(value) { p.edit().putBoolean("enable_floating_bottom_bar_blur", value).apply() }

    override var enableNavigationBadge: Boolean
        get() = p.getBoolean("enable_navigation_badge", false)
        set(value) { p.edit().putBoolean("enable_navigation_badge", value).apply() }

    override var pageScale: Float
        get() = p.getFloat("page_scale", 1.0f)
        set(value) { p.edit().putFloat("page_scale", value).apply() }

    override var moduleDescriptionMaxLines: Int
        get() = p.getInt("module_description_max_lines", 4)
        set(value) { p.edit().putInt("module_description_max_lines", value).apply() }
}
