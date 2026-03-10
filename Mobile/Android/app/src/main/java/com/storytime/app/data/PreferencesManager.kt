package com.storytime.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "storytime_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_KID_NAME = stringPreferencesKey("kid_name")
        private val KEY_BEDTIME_SOUND = stringPreferencesKey("bedtime_sound")
        private val KEY_SLEEP_TIMER_STORIES = intPreferencesKey("sleep_timer_stories")
        private const val DEFAULT_SERVER_URL = "http://10.0.2.2:3002"
    }

    val serverUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SERVER_URL] ?: DEFAULT_SERVER_URL
    }

    val kidName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_KID_NAME] ?: ""
    }

    val bedtimeSound: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BEDTIME_SOUND] ?: "whiteNoise"
    }

    val sleepTimerStories: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_SLEEP_TIMER_STORIES] ?: 0
    }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = url
        }
    }

    suspend fun setKidName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_KID_NAME] = name
        }
    }

    suspend fun setBedtimeSound(sound: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BEDTIME_SOUND] = sound
        }
    }

    suspend fun setSleepTimerStories(count: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SLEEP_TIMER_STORIES] = count
        }
    }
}
