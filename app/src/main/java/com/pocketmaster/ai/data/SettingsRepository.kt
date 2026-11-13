package com.pocketmaster.ai.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Secure settings storage (encrypted preferences) wrapped around DataStore.
 * Keys:
 *  PROVIDER_TYPE (GEMINI/CUSTOM), API_KEY, BASE_URL, GEMINI_MODEL,
 *  GEMINI_OAUTH_TOKEN, GEMINI_CLIENT_ID, SEARCH_PROVIDER, GOOGLE_CSE_ID,
 *  GOOGLE_CSE_API_KEY, LAST_USED_TAB
 */
class SettingsRepository internal constructor(
    private val context: Context,
    private val dataStoreManager: DataStoreManager
) {

    // Provider
    companion object {
        val PROVIDER_TYPE = stringPreferencesKey("provider_type")
        val API_KEY = stringPreferencesKey("api_key")
        val BASE_URL = stringPreferencesKey("base_url")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val GEMINI_OAUTH_TOKEN = stringPreferencesKey("gemini_oauth_token")
        val GEMINI_CLIENT_ID = stringPreferencesKey("gemini_client_id")
        val SEARCH_PROVIDER = stringPreferencesKey("search_provider")
        val GOOGLE_CSE_ID = stringPreferencesKey("google_cse_id")
        val GOOGLE_CSE_API_KEY = stringPreferencesKey("google_cse_api_key")
        val LAST_USED_TAB = intPreferencesKey("last_used_tab")

        // Tone rules
        val STRICT_VALIDATION = booleanPreferencesKey("strict_validation")
        val RANGE_CLAMP = booleanPreferencesKey("range_clamp")
        val WEB_SEARCH_DEFAULT = booleanPreferencesKey("web_search_default")
    }

    /** Current provider type: GEMINI or CUSTOM. */
    val providerType: Flow<String> = dataStoreManager.getString(PROVIDER_TYPE, "GEMINI")

    /** API key (provider-specific). */
    val apiKey: Flow<String> = dataStoreManager.getString(API_KEY, "")

    /** Custom base URL. */
    val baseUrl: Flow<String> = dataStoreManager.getString(BASE_URL, "")

    /** Selected Gemini model. */
    val geminiModel: Flow<String> = dataStoreManager.getString(GEMINI_MODEL, "gemini-2.5-flash")

    /** OAuth token for Google Sign-In. */
    val oauthToken: Flow<String?> = dataStoreManager.dataStore.data.map { prefs -> prefs[GEMINI_OAUTH_TOKEN] }

    /** OAuth client ID. */
    val clientId: Flow<String> = dataStoreManager.getString(GEMINI_CLIENT_ID, "")

    /** Search provider: DUCK_DUCK_GO or GOOGLE_CSE. */
    val searchProvider: Flow<String> = dataStoreManager.getString(SEARCH_PROVIDER, "DUCK_DUCK_GO")

    /** Google CSE ID. */
    val googleCseId: Flow<String> = dataStoreManager.getString(GOOGLE_CSE_ID, "")

    /** Google CSE API key. */
    val googleCseApiKey: Flow<String> = dataStoreManager.getString(GOOGLE_CSE_API_KEY, "")

    /** Last used navigation tab index. */
    val lastUsedTab: Flow<Int> = dataStoreManager.getInt(LAST_USED_TAB, 0)

    // Tone rules
    val strictValidation: Flow<Boolean> = dataStoreManager.getBoolean(STRICT_VALIDATION, true)
    val rangeClamp: Flow<Boolean> = dataStoreManager.getBoolean(RANGE_CLAMP, true)
    val webSearchDefault: Flow<Boolean> = dataStoreManager.getBoolean(WEB_SEARCH_DEFAULT, true)

    /** Whether OAuth + client ID appear configured. Exposed as a Flow for Compose state. */
    val hasOAuth: Flow<Boolean> = clientId.map { it.isNotEmpty() }

    suspend fun saveProviderType(type: String) = dataStoreManager.save(PROVIDER_TYPE, type)
    suspend fun saveApiKey(key: String) = dataStoreManager.save(API_KEY, key)
    suspend fun saveBaseUrl(url: String) = dataStoreManager.save(BASE_URL, url)
    suspend fun saveGeminiModel(model: String) = dataStoreManager.save(GEMINI_MODEL, model)
    suspend fun saveOauthToken(token: String?) = dataStoreManager.edit { prefs -> prefs[GEMINI_OAUTH_TOKEN] = token }
    suspend fun saveClientId(clientId: String) = dataStoreManager.save(GEMINI_CLIENT_ID, clientId)
    suspend fun saveSearchProvider(provider: String) = dataStoreManager.save(SEARCH_PROVIDER, provider)
    suspend fun saveGoogleCseId(id: String) = dataStoreManager.save(GOOGLE_CSE_ID, id)
    suspend fun saveGoogleCseApiKey(key: String) = dataStoreManager.save(GOOGLE_CSE_API_KEY, key)
    suspend fun saveLastUsedTab(tab: Int) = dataStoreManager.save(LAST_USED_TAB, tab)

    // Tone rules persistence
    suspend fun saveStrictValidation(strict: Boolean) = dataStoreManager.save(STRICT_VALIDATION, strict)
    suspend fun saveRangeClamp(clamp: Boolean) = dataStoreManager.save(RANGE_CLAMP, clamp)
    suspend fun saveWebSearchDefault(enable: Boolean) = dataStoreManager.save(WEB_SEARCH_DEFAULT, enable)

    /** Clear all user credentials. */
    suspend fun clearCredentials() {
        dataStoreManager.edit { prefs ->
            prefs[API_KEY] = ""
            prefs[BASE_URL] = ""
            prefs[GEMINI_OAUTH_TOKEN] = null
            prefs[GEMINI_CLIENT_ID] = ""
        }
    }
}
