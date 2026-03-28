package com.splitapp.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.splitapp.model.Friend
import com.splitapp.model.Group
import com.splitapp.model.Transaction
import com.splitapp.viewmodel.FriendSplit
import com.splitapp.viewmodel.SplitUiState
import com.splitapp.viewmodel.SplitViewModel
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SplitScreen(
    transactionId: Long,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SplitViewModel = viewModel()
) {
    LaunchedEffect(transactionId) { vm.load(transactionId) }

    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state) {
        if (state is SplitUiState.Success) {
            snackbarHostState.showSnackbar("Added to Splitwise!")
            onDone()
        }
    }

    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        when (val s = state) {
            is SplitUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is SplitUiState.Error -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }

            is SplitUiState.Ready -> {
                var showItemized by remember { mutableStateOf(false) }
                var currentUserId by remember { mutableStateOf<Long?>(null) }
                var pendingGroup by remember { mutableStateOf<Group?>(null) }

                // Resolve current user ID once
                LaunchedEffect(Unit) {
                    val prefs = com.splitapp.data.PreferencesStore(vm.getApplication())
                    currentUserId = prefs.currentUserId.first().toLongOrNull()
                }

                if (showItemized && currentUserId != null) {
                    // Init participants for itemized mode
                    LaunchedEffect(s.selectedSplits) {
                        vm.initItemizedMode(currentUserId!!, s.selectedSplits)
                    }
                    ItemizedSplitScreen(
                        vm = vm,
                        transaction = s.transaction,
                        currentUserId = currentUserId!!,
                        groupId = s.selectedGroupId,
                        description = s.description,
                        onBack = {
                            vm.clearItemizedState()
                            showItemized = false
                        },
                        modifier = Modifier.padding(padding)
                    )
                } else {
                    SplitContent(
                        modifier = Modifier.padding(padding),
                        transaction = s.transaction,
                        friends = s.friends,
                        groups = s.groups,
                        selectedSplits = s.selectedSplits,
                        selectedGroupId = s.selectedGroupId,
                        description = s.description,
                        pinnedFriendIds = s.pinnedFriendIds,
                        pinnedGroupIds = s.pinnedGroupIds,
                        onToggleGroup = { g ->
                            if (s.selectedGroupId == g.id || !vm.hasManualSplits(s)) {
                                vm.toggleGroup(g, s.transaction, s)
                            } else {
                                pendingGroup = g
                            }
                        },
                        onToggleFriend = { f -> vm.toggleFriend(f, s.transaction, s.selectedSplits) },
                        onUpdateRatio = { fId, r -> vm.updateRatio(fId, r, s.transaction, s.selectedSplits) },
                        onEqualSplit = { vm.applyEqualSplit(s.transaction, s.selectedSplits) },
                        onSubmit = { vm.submitSplit(s.transaction, s.selectedSplits, s.selectedGroupId, s.description) },
                        onSkip = { vm.skipTransaction(s.transaction.id); onDone() },
                        onItemizedSplit = {
                            if (s.selectedSplits.isNotEmpty()) showItemized = true
                        },
                        hasSelectedFriends = s.selectedSplits.isNotEmpty(),
                        onDescriptionChange = { vm.updateDescription(it) },
                        onTogglePinFriend = { vm.togglePinFriend(it) },
                        onTogglePinGroup = { vm.togglePinGroup(it) }
                    )
                }

                // Confirmation dialog when group selection would replace manual splits
                pendingGroup?.let { group ->
                    AlertDialog(
                        onDismissRequest = { pendingGroup = null },
                        title = { Text("Replace current splits?") },
                        text = {
                            Text("Selecting \"${group.name}\" will replace your current friend selections with the group's members.")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                vm.applyGroupSelection(group, s.transaction, s)
                                pendingGroup = null
                            }) { Text("Replace") }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingGroup = null }) { Text("Cancel") }
                        }
                    )
                }
            }

            else -> {}
        }
    }
}

@Composable
private fun SplitContent(
    modifier: Modifier,
    transaction: Transaction,
    friends: List<Friend>,
    groups: List<Group>,
    selectedSplits: List<FriendSplit>,
    selectedGroupId: Long?,
    description: String,
    pinnedFriendIds: Set<Long>,
    pinnedGroupIds: Set<Long>,
    onToggleGroup: (Group) -> Unit,
    onToggleFriend: (Friend) -> Unit,
    onUpdateRatio: (Long, Float) -> Unit,
    onEqualSplit: () -> Unit,
    onSubmit: () -> Unit,
    onSkip: () -> Unit,
    onItemizedSplit: () -> Unit = {},
    hasSelectedFriends: Boolean = false,
    onDescriptionChange: (String) -> Unit = {},
    onTogglePinFriend: (Long) -> Unit = {},
    onTogglePinGroup: (Long) -> Unit = {}
) {
    val df = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val totalRatio = selectedSplits.sumOf { it.ratio.toDouble() }.toFloat()
    val ratioValid = selectedSplits.isNotEmpty() && totalRatio <= 100f

    var searchQuery by remember { mutableStateOf("") }
    var groupSearchQuery by remember { mutableStateOf("") }

    // Groups filtered by search, pinned first
    val displayedGroups = remember(groups, selectedGroupId, groupSearchQuery, pinnedGroupIds) {
        groups.filter { group ->
            group.id == selectedGroupId ||
                groupSearchQuery.isBlank() ||
                group.name.contains(groupSearchQuery, ignoreCase = true)
        }.sortedByDescending { it.id in pinnedGroupIds }
    }

    // Friends: selected first, then pinned, then rest — all filtered by search
    val selectedIds = selectedSplits.map { it.friend.id }.toSet()
    val displayedFriends = remember(friends, selectedIds, searchQuery, pinnedFriendIds) {
        val selected = friends.filter { it.id in selectedIds }
            .sortedByDescending { it.id in pinnedFriendIds }
        val unselected = friends
            .filter { it.id !in selectedIds }
            .filter { friend ->
                searchQuery.isBlank() ||
                    friend.displayName.contains(searchQuery, ignoreCase = true) ||
                    friend.email?.contains(searchQuery, ignoreCase = true) == true
            }
            .sortedByDescending { it.id in pinnedFriendIds }
        selected + unselected
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Transaction card with editable title
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Transaction", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "₹%.2f".format(transaction.amount),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = onDescriptionChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Expense title") },
                        singleLine = true
                    )
                    Text(
                        df.format(Date(transaction.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Equal split + summary
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Your share: ₹%.2f".format(transaction.amount * (100f - totalRatio) / 100f),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = onEqualSplit, enabled = selectedSplits.isNotEmpty()) {
                    Text("Equal split")
                }
            }
        }

        // Itemized split button
        item {
            OutlinedButton(
                onClick = onItemizedSplit,
                modifier = Modifier.fillMaxWidth(),
                enabled = hasSelectedFriends
            ) {
                Icon(Icons.Default.Receipt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Itemized split (scan receipt)")
            }
        }

        // Groups section (only shown if there are groups)
        if (groups.isNotEmpty()) {
            item {
                Text("Split by group:", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = groupSearchQuery,
                    onValueChange = { groupSearchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search groups…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (groupSearchQuery.isNotBlank()) {
                            IconButton(onClick = { groupSearchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    displayedGroups.forEach { group ->
                        val isSelected = group.id == selectedGroupId
                        val isPinned = group.id in pinnedGroupIds
                        FilterChip(
                            selected = isSelected,
                            onClick = { onToggleGroup(group) },
                            label = { Text(group.name) },
                            leadingIcon = {
                                Icon(
                                    if (isPinned) Icons.Filled.PushPin else Icons.Default.Group,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { onTogglePinGroup(group.id) },
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                ) {
                                    Icon(
                                        if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                        contentDescription = if (isPinned) "Unpin" else "Pin",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        item {
            Text("Or select friends individually:", style = MaterialTheme.typography.titleMedium)
        }

        // Search field
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search friends…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true
            )
        }

        // Friends list — selected first, then filtered unselected
        items(displayedFriends, key = { it.id }) { friend ->
            val split = selectedSplits.find { it.friend.id == friend.id }
            FriendRow(
                friend = friend,
                split = split,
                totalAmount = transaction.amount,
                isPinned = friend.id in pinnedFriendIds,
                onToggle = { onToggleFriend(friend) },
                onRatioChange = { r -> onUpdateRatio(friend.id, r) },
                onTogglePin = { onTogglePinFriend(friend.id) }
            )
        }

        // Action buttons
        item {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) {
                    Text("Skip")
                }
                Button(
                    onClick = onSubmit,
                    modifier = Modifier.weight(1f),
                    enabled = ratioValid
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add to Splitwise")
                }
            }
        }
    }
}

@Composable
private fun FriendRow(
    friend: Friend,
    split: FriendSplit?,
    totalAmount: Double,
    isPinned: Boolean,
    onToggle: () -> Unit,
    onRatioChange: (Float) -> Unit,
    onTogglePin: () -> Unit
) {
    val isSelected = split != null
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(friend.displayName, style = MaterialTheme.typography.bodyLarge)
                    friend.email?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (isPinned) "Unpin" else "Pin",
                        modifier = Modifier.size(18.dp),
                        tint = if (isPinned) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
            }

            if (isSelected && split != null) {
                Spacer(Modifier.height(8.dp))
                var ratioText by remember(split.ratio) { mutableStateOf("%.1f".format(split.ratio)) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = ratioText,
                        onValueChange = { v ->
                            ratioText = v
                            v.toFloatOrNull()?.let { onRatioChange(it) }
                        },
                        label = { Text("Ratio %") },
                        modifier = Modifier.width(100.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    Text(
                        "= ₹%.2f".format(totalAmount * split.ratio / 100.0),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
