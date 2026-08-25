package com.fpsmeter.app

import android.content.Context
import android.graphics.Color

class PrefsManager(context: Context) {
    private val prefs = context.getSharedPreferences("fps_meter_prefs", Context.MODE_PRIVATE)

    var posX: Int
        get() = prefs.getInt("pos_x", 50)
        set(value) = prefs.edit().putInt("pos_x", value).apply()

    var posY: Int
        get() = prefs.getInt("pos_y", 150)
        set(value) = prefs.edit().putInt("pos_y", value).apply()

    var fontSize: Float
        get() = prefs.getFloat("font_size", 14f)
        set(value) = prefs.edit().putFloat("font_size", value).apply()

    var padding: Int
        get() = prefs.getInt("padding", 16)
        set(value) = prefs.edit().putInt("padding", value).apply()

    var cornerRadius: Int
        get() = prefs.getInt("corner_radius", 12)
        set(value) = prefs.edit().putInt("corner_radius", value).apply()

    var textColor: Int
        get() = prefs.getInt("text_color", Color.WHITE)
        set(value) = prefs.edit().putInt("text_color", value).apply()

    var backgroundColor: Int
        get() = prefs.getInt("background_color", Color.BLACK)
        set(value) = prefs.edit().putInt("background_color", value).apply()

    var backgroundAlpha: Int
        get() = prefs.getInt("background_alpha", 160)
        set(value) = prefs.edit().putInt("background_alpha", value).apply()

    var updateIntervalMs: Long
        get() = prefs.getLong("update_interval_ms", 1000L)
        set(value) = prefs.edit().putLong("update_interval_ms", value).apply()

    var overlayScale: Float
        get() = prefs.getFloat("overlay_scale", 1.0f)
        set(value) = prefs.edit().putFloat("overlay_scale", value).apply()

    var overlayEnabled: Boolean
        get() = prefs.getBoolean("overlay_enabled", false)
        set(value) = prefs.edit().putBoolean("overlay_enabled", value).apply()
}
