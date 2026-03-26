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
import androidx.compose.material.icons.filled.Search
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

            is SplitUiState.Ready -> SplitContent(
                modifier = Modifier.padding(padding),
                transaction = s.transaction,
                friends = s.friends,
                groups = s.groups,
                selectedSplits = s.selectedSplits,
                selectedGroupId = s.selectedGroupId,
                onToggleGroup = { g -> vm.toggleGroup(g, s.transaction, s) },
                onToggleFriend = { f -> vm.toggleFriend(f, s.transaction, s.selectedSplits) },
                onUpdateRatio = { fId, r -> vm.updateRatio(fId, r, s.transaction, s.selectedSplits) },
                onEqualSplit = { vm.applyEqualSplit(s.transaction, s.selectedSplits) },
                onSubmit = { vm.submitSplit(s.transaction, s.selectedSplits, s.selectedGroupId) },
                onSkip = { vm.skipTransaction(s.transaction.id); onDone() }
            )

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
    onToggleGroup: (Group) -> Unit,
    onToggleFriend: (Friend) -> Unit,
    onUpdateRatio: (Long, Float) -> Unit,
    onEqualSplit: () -> Unit,
    onSubmit: () -> Unit,
    onSkip: () -> Unit
) {
    val df = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val totalRatio = selectedSplits.sumOf { it.ratio.toDouble() }.toFloat()
    val ratioValid = selectedSplits.isNotEmpty() && totalRatio <= 100f

    var searchQuery by remember { mutableStateOf("") }
    var groupSearchQuery by remember { mutableStateOf("") }

    // Groups filtered by group search query (selected group always shown)
    val displayedGroups = remember(groups, selectedGroupId, groupSearchQuery) {
        groups.filter { group ->
            group.id == selectedGroupId ||
                groupSearchQuery.isBlank() ||
                group.name.contains(groupSearchQuery, ignoreCase = true)
        }
    }

    // Selected friends always shown first; unselected filtered by search query
    val selectedIds = selectedSplits.map { it.friend.id }.toSet()
    val displayedFriends = remember(friends, selectedIds, searchQuery) {
        val selected = friends.filter { it.id in selectedIds }
        val unselected = friends
            .filter { it.id !in selectedIds }
            .filter { friend ->
                searchQuery.isBlank() ||
                    friend.displayName.contains(searchQuery, ignoreCase = true) ||
                    friend.email?.contains(searchQuery, ignoreCase = true) == true
            }
        selected + unselected
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Transaction card
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Transaction", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "₹%.2f".format(transaction.amount),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(transaction.description, style = MaterialTheme.typography.bodyMedium)
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
                        FilterChip(
                            selected = isSelected,
                            onClick = { onToggleGroup(group) },
                            label = { Text(group.name) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Group,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
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
                onToggle = { onToggleFriend(friend) },
                onRatioChange = { r -> onUpdateRatio(friend.id, r) }
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
    onToggle: () -> Unit,
    onRatioChange: (Float) -> Unit
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
                Column {
                    Text(friend.displayName, style = MaterialTheme.typography.bodyLarge)
                    friend.email?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
