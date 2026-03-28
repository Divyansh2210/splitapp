package com.splitapp.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitapp.SplitApplication
import com.splitapp.api.GeminiReceiptExtractor
import com.splitapp.api.SplitwiseRepository
import com.splitapp.data.PreferencesStore
import com.splitapp.model.Friend
import com.splitapp.model.Group
import com.splitapp.model.OrderItem
import com.splitapp.model.Transaction
import com.splitapp.model.TransactionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

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
        val selectedGroupId: Long? = null,
        val description: String = "",
        val pinnedFriendIds: Set<Long> = emptySet(),
        val pinnedGroupIds: Set<Long> = emptySet()
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
            val pinnedFriends = prefs.pinnedFriendIds.first()
            val pinnedGroups = prefs.pinnedGroupIds.first()
            _uiState.value = SplitUiState.Ready(
                transaction = transaction,
                friends = friends,
                groups = groups,
                selectedSplits = emptyList(),
                description = transaction.description,
                pinnedFriendIds = pinnedFriends,
                pinnedGroupIds = pinnedGroups
            )
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

    fun updateDescription(description: String) {
        val s = uiState.value as? SplitUiState.Ready ?: return
        _uiState.value = s.copy(description = description)
    }

    fun togglePinFriend(friendId: Long) {
        val s = uiState.value as? SplitUiState.Ready ?: return
        val newPins = if (friendId in s.pinnedFriendIds) {
            s.pinnedFriendIds - friendId
        } else {
            s.pinnedFriendIds + friendId
        }
        _uiState.value = s.copy(pinnedFriendIds = newPins)
        viewModelScope.launch { prefs.setPinnedFriendIds(newPins) }
    }

    fun togglePinGroup(groupId: Long) {
        val s = uiState.value as? SplitUiState.Ready ?: return
        val newPins = if (groupId in s.pinnedGroupIds) {
            s.pinnedGroupIds - groupId
        } else {
            s.pinnedGroupIds + groupId
        }
        _uiState.value = s.copy(pinnedGroupIds = newPins)
        viewModelScope.launch { prefs.setPinnedGroupIds(newPins) }
    }

    fun submitSplit(transaction: Transaction, splits: List<FriendSplit>, groupId: Long?, description: String) {
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
                description = description,
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

    // =========================================================================
    // Itemized split — OCR + bucket assignment
    // =========================================================================

    data class ItemizedUiState(
        val items: List<OrderItem> = emptyList(),
        val buckets: Map<String, Set<Long>> = emptyMap(),   // itemId → participant IDs
        val allParticipantIds: Set<Long> = emptySet(),
        val participantNames: Map<Long, String> = emptyMap(),
        val isProcessingOcr: Boolean = false,
        val ocrError: String? = null
    ) {
        companion object { val Idle = ItemizedUiState() }
    }

    private val _itemizedState = MutableStateFlow(ItemizedUiState.Idle)
    val itemizedState: StateFlow<ItemizedUiState> = _itemizedState

    /** Call before entering itemized screen. Sets up participants from current selection. */
    fun initItemizedMode(currentUserId: Long, selectedFriends: List<FriendSplit>) {
        val newIds = setOf(currentUserId) + selectedFriends.map { it.friend.id }.toSet()
        val names = mutableMapOf(currentUserId to "You")
        selectedFriends.forEach { names[it.friend.id] = it.friend.displayName }

        val current = _itemizedState.value
        if (current.items.isEmpty()) {
            _itemizedState.value = ItemizedUiState(
                allParticipantIds = newIds,
                participantNames = names
            )
        } else {
            // Re-entering with existing items — remap buckets for changed participants
            val newBuckets = current.buckets.mapValues { (_, bucket) ->
                val remapped = bucket.intersect(newIds)
                if (remapped.isEmpty()) newIds else remapped
            }
            _itemizedState.value = current.copy(
                allParticipantIds = newIds,
                participantNames = names,
                buckets = newBuckets
            )
        }
    }

    /** Send receipt image to Gemini VLM for structured item extraction. */
    fun processReceiptImage(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            _itemizedState.value = _itemizedState.value.copy(isProcessingOcr = true, ocrError = null)
            try {
                val extractor = GeminiReceiptExtractor(context)
                val items = extractor.extractItems(imageUri)
                if (items.isEmpty()) {
                    _itemizedState.value = _itemizedState.value.copy(
                        isProcessingOcr = false,
                        ocrError = "No items found. Try a clearer screenshot or add items manually."
                    )
                    return@launch
                }
                val allIds = _itemizedState.value.allParticipantIds
                val buckets = items.associate { it.id to allIds }  // default: everyone
                _itemizedState.value = _itemizedState.value.copy(
                    items = items,
                    buckets = buckets,
                    isProcessingOcr = false
                )
            } catch (e: Exception) {
                _itemizedState.value = _itemizedState.value.copy(
                    isProcessingOcr = false,
                    ocrError = "Receipt scan failed: ${e.message}"
                )
            }
        }
    }

    fun assignBucket(itemId: String, participantIds: Set<Long>) {
        val current = _itemizedState.value
        _itemizedState.value = current.copy(
            buckets = current.buckets + (itemId to participantIds)
        )
    }

    fun addItem(name: String, price: Double, isFee: Boolean = false) {
        val current = _itemizedState.value
        val item = OrderItem(name = name, price = price, isFee = isFee)
        val allIds = current.allParticipantIds
        _itemizedState.value = current.copy(
            items = current.items + item,
            buckets = current.buckets + (item.id to allIds)
        )
    }

    fun editItem(itemId: String, newName: String, newPrice: Double) {
        val current = _itemizedState.value
        _itemizedState.value = current.copy(
            items = current.items.map {
                if (it.id == itemId) it.copy(name = newName, price = newPrice) else it
            }
        )
    }

    fun deleteItem(itemId: String) {
        val current = _itemizedState.value
        _itemizedState.value = current.copy(
            items = current.items.filter { it.id != itemId },
            buckets = current.buckets - itemId
        )
    }

    fun clearItemizedState() {
        _itemizedState.value = ItemizedUiState.Idle
    }

    /** Compute how much each participant owes based on item bucket assignments. */
    fun computePerPersonTotals(): Map<Long, Double> {
        val state = _itemizedState.value
        val totals = state.allParticipantIds.associateWith { 0.0 }.toMutableMap()
        for (item in state.items) {
            val bucket = state.buckets[item.id] ?: state.allParticipantIds
            if (bucket.isEmpty()) continue
            val share = item.price / bucket.size
            for (pid in bucket) {
                totals[pid] = (totals[pid] ?: 0.0) + share
            }
        }
        return totals
    }

    /** Submit the itemized split to Splitwise. */
    fun submitItemizedSplit(transaction: Transaction, groupId: Long?) {
        viewModelScope.launch {
            val readyState = _uiState.value as? SplitUiState.Ready
            val description = readyState?.description ?: transaction.description
            _uiState.value = SplitUiState.Loading
            val currentUserId = prefs.currentUserId.first().toLongOrNull()
            if (currentUserId == null) {
                _uiState.value = SplitUiState.Error("User ID not found — please sync in Settings first")
                return@launch
            }

            val perPerson = computePerPersonTotals()
            val itemTotal = _itemizedState.value.items.sumOf { it.price }

            // Round each share to 2 decimal places
            val friendSplits = perPerson
                .filter { it.key != currentUserId }
                .map { (friendId, amount) ->
                    Pair(friendId, BigDecimal(amount).setScale(2, RoundingMode.HALF_UP).toDouble())
                }

            repo.createExpense(
                totalAmount = BigDecimal(itemTotal).setScale(2, RoundingMode.HALF_UP).toDouble(),
                description = description,
                currentUserId = currentUserId,
                splits = friendSplits,
                groupId = groupId
            ).onSuccess {
                db.transactionDao().updateStatus(transaction.id, TransactionStatus.DONE)
                _itemizedState.value = ItemizedUiState.Idle
                _uiState.value = SplitUiState.Success
            }.onFailure {
                _uiState.value = SplitUiState.Error(it.message ?: "Unknown error")
            }
        }
    }
}
