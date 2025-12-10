package com.ningyu.novelreader.ui.screens

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

class ReadingSettings(context: Context) {
    private val prefs = context.getSharedPreferences("reading_settings", Context.MODE_PRIVATE)

    var fontSizeSp by mutableFloatStateOf(prefs.getFloat("font_size", 19f))

    var lineHeightMultiplier by mutableFloatStateOf(prefs.getFloat("line_height", 1.7f))

    var isNightMode by mutableStateOf(prefs.getBoolean("night_mode", false))

    fun save() {
        prefs.edit {
            putFloat("font_size", fontSizeSp)
            putFloat("line_height", lineHeightMultiplier)
            putBoolean("night_mode", isNightMode)
        }
    }
}

@Composable
fun rememberReadingSettings(): ReadingSettings {
    val context = LocalContext.current
    return remember(context) {
        ReadingSettings(context)
    }
}