package com.pocketmaster.ai.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * Wraps AndroidX DataStore Preferences:
 * - One DataStore per app
 * - typed save/get via Preferences keys
 * - read/write with coroutines
 */
class DataStoreManager internal constructor(private val context: Context) {

    /** The app-level DataStore instance (created in the companion object). */
    private val dataStore: DataStore<Preferences> = context.preferencesDataStore("pocketmaster_settings")

    companion object {
        private var instance: DataStoreManager? = null
        fun create(context: Context): DataStoreManager =
            instance ?: DataStoreManager(context).also { instance = it }
    }

    /** Read a string value (or defaultValue) as a Flow. */
    fun getString(key: Preferences.Key<String>, defaultValue: String = ""): Flow<String> =
        dataStore.data.map { prefs -> prefs[key] ?: defaultValue }

    /** Read an int value as a Flow. */
    fun getInt(key: Preferences.Key<Int>, defaultValue: Int = 0): Flow<Int> =
        dataStore.data.map { prefs -> prefs[key] ?: defaultValue }

    /** Read a boolean value as a Flow. */
    fun getBoolean(key: Preferences.Key<Boolean>, defaultValue: Boolean = false): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[key] ?: defaultValue }

    /** Write a string value. */
    suspend fun save(key: Preferences.Key<String>, value: String) {
        dataStore.edit { prefs -> prefs[key] = value }
    }

    /** Write an int value. */
    suspend fun save(key: Preferences.Key<Int>, value: Int) {
        dataStore.edit { prefs -> prefs[key] = value }
    }

    /** Write a boolean value. */
    suspend fun save(key: Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { prefs -> prefs[key] = value }
    }

    /** Edit multiple keys in one transaction. */
    suspend fun edit(block: suspend (MutablePreferences) -> Unit) {
        dataStore.edit { prefs -> block(prefs) }
    }

    /** Convenience: set a string from Compose-side state (stringPreferencesKey). */
    suspend fun setString(key: String, value: String) = save(stringPreferencesKey(key), value)

    /** Convenience: set an int. */
    suspend fun setInt(key: String, value: Int) = save(intPreferencesKey(key), value)

    /** Convenience: set a boolean. */
    suspend fun setBoolean(key: String, value: Boolean) = save(booleanPreferencesKey(key), value)
}
