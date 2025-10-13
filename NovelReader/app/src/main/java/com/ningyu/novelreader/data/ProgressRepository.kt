package com.ningyu.novelreader.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore("reading_progress")

class ProgressRepository(private val context: Context) {

    private val PAGE_KEY_PREFIX = "progress_"

    fun getProgress(title: String): Flow<Int> {
        val key = intPreferencesKey(PAGE_KEY_PREFIX + title)
        return context.dataStore.data.map { prefs -> prefs[key] ?: 0 }
    }

    suspend fun saveProgress(title: String, page: Int) {
        val key = intPreferencesKey(PAGE_KEY_PREFIX + title)
        context.dataStore.edit { prefs -> prefs[key] = page }
    }
}
