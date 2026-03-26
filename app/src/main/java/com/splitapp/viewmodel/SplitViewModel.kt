package com.splitapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitapp.SplitApplication
import com.splitapp.api.SplitwiseRepository
import com.splitapp.data.PreferencesStore
import com.splitapp.model.Friend
import com.splitapp.model.Group
import com.splitapp.model.Transaction
import com.splitapp.model.TransactionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class FriendSplit(
    val friend: Friend,
    val ratio: Float   // 0..100 percentage
)

sealed class SplitUiState {
    object Loading : SplitUiState()
    data class Ready(
        val transaction: Transaction,
        val friends: List<Friend>,
        val groups: List<Group>,
        val selectedSplits: List<FriendSplit>,
        val selectedGroupId: Long? = null
    ) : SplitUiState()
    data class Error(val message: String) : SplitUiState()
    object Success : SplitUiState()
}

class SplitViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesStore(application)
    private val db = SplitApplication.database(application)
    private val repo = SplitwiseRepository(prefs)

    private val _uiState = MutableStateFlow<SplitUiState>(SplitUiState.Loading)
    val uiState: StateFlow<SplitUiState> = _uiState

    fun load(transactionId: Long) {
        viewModelScope.launch {
            val transaction = db.transactionDao().getById(transactionId)
            if (transaction == null) {
                _uiState.value = SplitUiState.Error("Transaction not found")
                return@launch
            }
            // Ensure user ID is cached so submit never needs to re-fetch it
            if (prefs.currentUserId.first().isBlank()) {
                repo.getCurrentUserId().onFailure {
                    _uiState.value = SplitUiState.Error("Could not load your Splitwise user: ${it.message}")
                    return@launch
                }
            }
            val friends = repo.getCachedFriends().ifEmpty {
                repo.syncFriends().getOrElse { emptyList() }
            }
            val groups = repo.getCachedGroups().ifEmpty {
                repo.syncGroups().getOrElse { emptyList() }
            }
            _uiState.value = SplitUiState.Ready(transaction, friends, groups, emptyList())
        }
    }

    /** Tap a group chip — selects it and pre-populates all its members into splits. Tap again to deselect. */
    fun toggleGroup(group: Group, transaction: Transaction, current: SplitUiState.Ready) {
        if (current.selectedGroupId == group.id) {
            // Deselect group — clear group_id but keep existing splits
            _uiState.value = current.copy(selectedGroupId = null)
        } else {
            // Select group — replace splits with all group members, equal split
            val newSplits = group.members.map { FriendSplit(it, 0f) }
            val rebalanced = equalSplit(newSplits, transaction.amount)
            _uiState.value = current.copy(selectedGroupId = group.id, selectedSplits = rebalanced)
        }
    }

    fun toggleFriend(friend: Friend, transaction: Transaction, currentSplits: List<FriendSplit>) {
        val existing = currentSplits.find { it.friend.id == friend.id }
        val newSplits = if (existing != null) {
            currentSplits.filter { it.friend.id != friend.id }
        } else {
            currentSplits + FriendSplit(friend, 0f)
        }
        val rebalanced = equalSplit(newSplits, transaction.amount)
        val s = uiState.value as SplitUiState.Ready
        _uiState.value = s.copy(selectedSplits = rebalanced)
    }

    fun updateRatio(friendId: Long, ratio: Float, transaction: Transaction, currentSplits: List<FriendSplit>) {
        val newSplits = currentSplits.map {
            if (it.friend.id == friendId) it.copy(ratio = ratio.coerceIn(0f, 100f)) else it
        }
        val s = uiState.value as SplitUiState.Ready
        _uiState.value = s.copy(selectedSplits = newSplits)
    }

    fun applyEqualSplit(transaction: Transaction, currentSplits: List<FriendSplit>) {
        val rebalanced = equalSplit(currentSplits, transaction.amount)
        val s = uiState.value as SplitUiState.Ready
        _uiState.value = s.copy(selectedSplits = rebalanced)
    }

    fun submitSplit(transaction: Transaction, splits: List<FriendSplit>, groupId: Long?) {
        viewModelScope.launch {
            _uiState.value = SplitUiState.Loading
            val currentUserId = prefs.currentUserId.first().toLongOrNull()
            if (currentUserId == null) {
                _uiState.value = SplitUiState.Error("User ID not found — please sync in Settings first")
                return@launch
            }

            val splitAmounts = splits.map { fs ->
                val amount = transaction.amount * fs.ratio / 100.0
                Pair(fs.friend.id, amount)
            }

            repo.createExpense(
                totalAmount = transaction.amount,
                description = transaction.description,
                currentUserId = currentUserId,
                splits = splitAmounts,
                groupId = groupId
            ).onSuccess {
                db.transactionDao().updateStatus(transaction.id, TransactionStatus.DONE)
                _uiState.value = SplitUiState.Success
            }.onFailure {
                _uiState.value = SplitUiState.Error(it.message ?: "Unknown error")
            }
        }
    }

    fun skipTransaction(transactionId: Long) {
        viewModelScope.launch {
            db.transactionDao().updateStatus(transactionId, TransactionStatus.SKIPPED)
        }
    }

    private fun equalSplit(splits: List<FriendSplit>, amount: Double): List<FriendSplit> {
        if (splits.isEmpty()) return splits
        val perPerson = 100f / (splits.size + 1)  // +1 for the current user
        return splits.map { it.copy(ratio = perPerson) }
    }
}
