package ru.rutubeclient.mobile.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** User settings persisted in DataStore. */
class SettingsRepository(private val context: Context) {

    /** Continue playing audio when the app goes to background. */
    val backgroundPlay: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_BACKGROUND_PLAY] ?: true
    }

    /** Preferred video height; 0 = auto. */
    val preferredQuality: Flow<Int> = context.dataStore.data.map {
        it[KEY_QUALITY] ?: 0
    }

    suspend fun setBackgroundPlay(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BACKGROUND_PLAY] = enabled }
    }

    suspend fun setPreferredQuality(height: Int) {
        context.dataStore.edit { it[KEY_QUALITY] = height }
    }

    private companion object {
        val KEY_BACKGROUND_PLAY = booleanPreferencesKey("background_play")
        val KEY_QUALITY = intPreferencesKey("preferred_quality")
    }
}
