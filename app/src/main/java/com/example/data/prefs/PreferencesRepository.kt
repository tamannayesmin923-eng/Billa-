package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.i18n.AppLanguage
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.tammieDataStore: DataStore<Preferences> by preferencesDataStore(name = "tammie_prefs")

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.EN,
    val notificationsEnabled: Boolean = true
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language_code")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
    }

    val preferencesFlow: Flow<UserPreferences> = context.tammieDataStore.data.map { prefs ->
        val theme = prefs[Keys.THEME_MODE]?.let { raw ->
            ThemeMode.entries.firstOrNull { it.name == raw }
        } ?: ThemeMode.SYSTEM
        val lang = prefs[Keys.LANGUAGE]?.let { code ->
            AppLanguage.entries.firstOrNull { it.code == code }
        } ?: AppLanguage.EN
        val notifications = prefs[Keys.NOTIFICATIONS] ?: true
        UserPreferences(
            themeMode = theme,
            language = lang,
            notificationsEnabled = notifications
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.tammieDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.tammieDataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.tammieDataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }
}
