package com.splitapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitapp.api.SplitwiseRepository
import com.splitapp.data.PreferencesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class SettingsUiState {
    object Idle : SettingsUiState()
    object SyncInProgress : SettingsUiState()
    data class SyncSuccess(val friendCount: Int, val groupCount: Int) : SettingsUiState()
    data class SyncError(val message: String) : SettingsUiState()
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PreferencesStore(app)
    private val repo = SplitwiseRepository(prefs)

    val apiKey = prefs.apiKey
    val geminiApiKey = prefs.geminiApiKey

    private val _syncState = MutableStateFlow<SettingsUiState>(SettingsUiState.Idle)
    val syncState: StateFlow<SettingsUiState> = _syncState

    fun saveApiKey(key: String) {
        viewModelScope.launch { prefs.setApiKey(key) }
    }

    fun saveGeminiApiKey(key: String) {
        viewModelScope.launch { prefs.setGeminiApiKey(key) }
    }

    fun syncFriendsAndGroups() {
        viewModelScope.launch {
            _syncState.value = SettingsUiState.SyncInProgress
            repo.getCurrentUserId()
            val friendsResult = repo.syncFriends()
            val groupsResult = repo.syncGroups()
            if (friendsResult.isFailure) {
                _syncState.value = SettingsUiState.SyncError(friendsResult.exceptionOrNull()?.message ?: "Sync failed")
                return@launch
            }
            val friendCount = friendsResult.getOrDefault(emptyList()).size
            val groupCount = groupsResult.getOrDefault(emptyList()).size
            _syncState.value = SettingsUiState.SyncSuccess(friendCount, groupCount)
        }
    }
}
