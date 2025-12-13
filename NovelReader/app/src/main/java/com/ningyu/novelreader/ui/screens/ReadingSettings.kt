package com.ningyu.novelreader.ui.screens

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

/**
 * Manages user preferences for reading experience:
 * - Font size
 * - Line height
 * - Night mode
 *
 * Values are persisted in SharedPreferences.
 */
class ReadingSettings(context: Context) {
    private val prefs = context.getSharedPreferences("reading_prefs", Context.MODE_PRIVATE)

    var fontSizeSp by mutableFloatStateOf(prefs.getFloat("font_size", 19f))
        private set

    var lineHeightMultiplier by mutableFloatStateOf(prefs.getFloat("line_height", 1.7f))
        private set

    var isNightMode by mutableStateOf(prefs.getBoolean("night_mode", false))
        private set

    /** Call this when the user confirms changes in the settings dialog */
    fun updateFontSize(value: Float) {
        fontSizeSp = value
    }

    fun updateLineHeight(value: Float) {
        lineHeightMultiplier = value
    }

    fun toggleNightMode(enabled: Boolean) {
        isNightMode = enabled
    }

    /** Persists current values to SharedPreferences */
    fun save() {
        prefs.edit {
            putFloat("font_size", fontSizeSp)
            putFloat("line_height", lineHeightMultiplier)
            putBoolean("night_mode", isNightMode)
        }
    }
}

/**
 * Composable function to remember a single instance of ReadingSettings
 * throughout the composition lifecycle.
 */
@Composable
fun rememberReadingSettings(): ReadingSettings {
    val context = LocalContext.current
    return remember { ReadingSettings(context) }
}