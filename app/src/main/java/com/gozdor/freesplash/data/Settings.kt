package com.gozdor.freesplash.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val apiKey: String = "",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
)

class SettingsStore(private val context: Context) {
    private val keyApi = stringPreferencesKey("api_key")
    private val keyTheme = stringPreferencesKey("theme")
    private val keyDynamic = booleanPreferencesKey("dynamic_color")

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            apiKey = p[keyApi].orEmpty(),
            theme = p[keyTheme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColor = p[keyDynamic] ?: true,
        )
    }

    suspend fun setApiKey(value: String) = context.dataStore.edit { it[keyApi] = value.trim() }
    suspend fun setTheme(value: ThemeMode) = context.dataStore.edit { it[keyTheme] = value.name }
    suspend fun setDynamicColor(value: Boolean) = context.dataStore.edit { it[keyDynamic] = value }
}
