package com.example.rickandmortyexplorer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Оголошуємо делегат preferencesDataStore один раз на рівні файлу
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class SettingsManager(private val context: Context) {

    companion object {
        // Ключ для збереження розширеного відображення деталей
        val SHOW_EXTENDED_DETAILS_KEY = booleanPreferencesKey("show_extended_details")
        val FAVORITE_IDS_KEY = stringSetPreferencesKey("favorite_character_ids")
        val DARK_THEME_KEY = booleanPreferencesKey("dark_theme_enabled")
    }

    // Читання значення як Flow з дефолтним значенням false (для першого запуску)
    val showExtendedDetailsFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[SHOW_EXTENDED_DETAILS_KEY] ?: false
        }

    val darkThemeFlow: Flow<Boolean?> = context.dataStore.data
        .map { preferences ->
            preferences[DARK_THEME_KEY]
        }

    val favoriteIdsFlow: Flow<Set<Int>> = context.dataStore.data
        .map { preferences ->
            preferences[FAVORITE_IDS_KEY]
                .orEmpty()
                .mapNotNull { it.toIntOrNull() }
                .toSet()
        }

    // Запис значення через suspend-функцію
    suspend fun setExtendedDetails(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_EXTENDED_DETAILS_KEY] = enabled
        }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DARK_THEME_KEY] = enabled
        }
    }

    suspend fun toggleFavorite(characterId: Int) {
        val idStr = characterId.toString()
        context.dataStore.edit { preferences ->
            val current = preferences[FAVORITE_IDS_KEY] ?: emptySet()
            preferences[FAVORITE_IDS_KEY] = if (idStr in current) {
                current - idStr
            } else {
                current + idStr
            }
        }
    }
}