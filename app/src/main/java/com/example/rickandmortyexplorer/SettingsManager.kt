package com.example.rickandmortyexplorer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Оголошуємо делегат preferencesDataStore один раз на рівні файлу
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class SettingsManager(private val context: Context) {

    companion object {
        // Ключ для збереження розширеного відображення деталей
        val SHOW_EXTENDED_DETAILS_KEY = booleanPreferencesKey("show_extended_details")
    }

    // Читання значення як Flow з дефолтним значенням false (для першого запуску)
    val showExtendedDetailsFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[SHOW_EXTENDED_DETAILS_KEY] ?: false
        }

    // Запис значення через suspend-функцію
    suspend fun setExtendedDetails(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_EXTENDED_DETAILS_KEY] = enabled
        }
    }
}