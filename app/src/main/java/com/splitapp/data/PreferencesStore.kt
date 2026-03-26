package com.splitapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "splitapp_prefs")

class PreferencesStore(private val context: Context) {

    companion object {
        val API_KEY = stringPreferencesKey("splitwise_api_key")
        val MONITORING_ENABLED = booleanPreferencesKey("sms_monitoring_enabled")
        val CACHED_FRIENDS = stringPreferencesKey("cached_friends_json")
        val CACHED_GROUPS = stringPreferencesKey("cached_groups_json")
        val CURRENT_USER_ID = stringPreferencesKey("current_user_id")
    }

    val apiKey: Flow<String> = context.dataStore.data.map { it[API_KEY] ?: "" }
    val monitoringEnabled: Flow<Boolean> = context.dataStore.data.map { it[MONITORING_ENABLED] ?: true }
    val cachedFriendsJson: Flow<String> = context.dataStore.data.map { it[CACHED_FRIENDS] ?: "" }
    val cachedGroupsJson: Flow<String> = context.dataStore.data.map { it[CACHED_GROUPS] ?: "" }
    val currentUserId: Flow<String> = context.dataStore.data.map { it[CURRENT_USER_ID] ?: "" }

    suspend fun setApiKey(key: String) = context.dataStore.edit { it[API_KEY] = key }
    suspend fun setMonitoringEnabled(enabled: Boolean) = context.dataStore.edit { it[MONITORING_ENABLED] = enabled }
    suspend fun setCachedFriends(json: String) = context.dataStore.edit { it[CACHED_FRIENDS] = json }
    suspend fun setCachedGroups(json: String) = context.dataStore.edit { it[CACHED_GROUPS] = json }
    suspend fun setCurrentUserId(id: String) = context.dataStore.edit { it[CURRENT_USER_ID] = id }
}
