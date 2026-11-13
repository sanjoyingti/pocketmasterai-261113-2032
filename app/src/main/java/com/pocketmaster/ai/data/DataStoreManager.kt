package com.pocketmaster.ai.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore

/**
 * Wraps AndroidX DataStore Preferences:
 * - One DataStore per app
 * - typed save/get via Preferences keys
 * - read/write with coroutines
 */
class DataStoreManager internal constructor(private val context: Context) {

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pocketmaster_settings")
    }

    val preferences: DataStore<Preferences> by context.dataStore

    /** Read a string value (or defaultValue). */
    suspend fun getString(key: Preferences.Key<String>, defaultValue: String = ""): String {
        return preferences.data.map { it[key] ?: defaultValue }.first()
    }

    /** Read an int value. */
    suspend fun getInt(key: Preferences.Key<Int>, defaultValue: Int = 0): Int {
        return preferences.data.map { it[key] ?: defaultValue }.first()
    }

    /** Read a boolean value. */
    suspend fun getBoolean(key: Preferences.Key<Boolean>, defaultValue: Boolean = false): Boolean {
        return preferences.data.map { it[key] ?: defaultValue }.first()
    }

    /** Write a string value. */
    suspend fun save(key: Preferences.Key<String>, value: String) {
        preferences.edit { it[key] = value }
    }

    /** Write an int value. */
    suspend fun save(key: Preferences.Key<Int>, value: Int) {
        preferences.edit { it[key] = value }
    }

    /** Write a boolean value. */
    suspend fun save(key: Preferences.Key<Boolean>, value: Boolean) {
        preferences.edit { it[key] = value }
    }

    /** Edit multiple keys in one transaction. */
    suspend fun edit(block: suspend (Preferences) -> Unit) {
        preferences.edit { prefs -> block(prefs) }
    }

    companion object {
        private var instance: DataStoreManager? = null

        fun create(context: Context): DataStoreManager =
            instance ?: DataStoreManager(context).also { instance = it }
    }
}
