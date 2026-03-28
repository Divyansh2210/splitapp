package com.splitapp.ui

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.splitapp.model.OrderItem
import com.splitapp.model.Transaction
import com.splitapp.viewmodel.SplitViewModel
import java.io.File
import kotlin.math.abs

@Composable
fun ItemizedSplitScreen(
    vm: SplitViewModel,
    transaction: Transaction,
    currentUserId: Long,
    groupId: Long?,
    description: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val itemizedState by vm.itemizedState.collectAsState()

    // Camera URI state
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    // Add item dialog
    var showAddDialog by remember { mutableStateOf(false) }

    // Edit item dialog
    var editingItem by remember { mutableStateOf<OrderItem?>(null) }

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { vm.processReceiptImage(context, it) } }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success) cameraUri?.let { vm.processReceiptImage(context, it) } }

    // Camera permission
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createImageUri(context)
            cameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    // Per-person totals
    val perPersonTotals = remember(itemizedState.items, itemizedState.buckets, itemizedState.allParticipantIds) {
        vm.computePerPersonTotals()
    }

    val itemTotal = remember(itemizedState.items) {
        itemizedState.items.sumOf { it.price }
    }

    val mismatch = abs(itemTotal - transaction.amount)

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Transaction header
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Itemized Split", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${transaction.description} — ₹%.2f".format(transaction.amount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Image capture (shown when no items yet and not processing)
        if (itemizedState.items.isEmpty() && !itemizedState.isProcessingOcr) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Receipt,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Scan your receipt or add items manually",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Take Photo")
                            }
                            Button(
                                onClick = { galleryLauncher.launch("image/*") }
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Pick Image")
                            }
                        }
                        TextButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Add items manually")
                        }
                    }
                }
            }
        }

        // OCR loading
        if (itemizedState.isProcessingOcr) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("Scanning receipt...")
                    }
                }
            }
        }

        // OCR error
        itemizedState.ocrError?.let { error ->
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = { showAddDialog = true }) {
                            Text("Add items manually")
                        }
                    }
                }
            }
        }

        // Mismatch warning
        if (itemizedState.items.isNotEmpty() && mismatch > 1.0) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Item total (₹%.2f) differs from transaction (₹%.2f) by ₹%.2f".format(
                                itemTotal, transaction.amount, mismatch
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        TextButton(onClick = {
                            val diff = transaction.amount - itemTotal
                            if (diff > 0) {
                                vm.addItem("Adjustment", diff, isFee = false)
                            }
                        }) {
                            Text("Add adjustment item")
                        }
                    }
                }
            }
        }

        // Item list
        if (itemizedState.items.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Items (${itemizedState.items.size})", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add item")
                    }
                }
            }

            items(itemizedState.items, key = { it.id }) { item ->
                ItemRow(
                    item = item,
                    bucket = itemizedState.buckets[item.id] ?: itemizedState.allParticipantIds,
                    allParticipantIds = itemizedState.allParticipantIds,
                    participantNames = itemizedState.participantNames,
                    onBucketChange = { ids -> vm.assignBucket(item.id, ids) },
                    onEdit = { editingItem = item },
                    onDelete = { vm.deleteItem(item.id) }
                )
            }
        }

        // Per-person breakdown
        if (perPersonTotals.isNotEmpty() && itemizedState.items.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Per-person breakdown", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        perPersonTotals.forEach { (pid, amount) ->
                            val name = itemizedState.participantNames[pid] ?: "Unknown"
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "₹%.2f".format(amount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "₹%.2f".format(perPersonTotals.values.sum()),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Action buttons
        item {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text("Back")
                }
                if (itemizedState.items.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            vm.clearItemizedState()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Re-scan")
                    }
                    Button(
                        onClick = { vm.submitItemizedSplit(transaction, groupId, description) },
                        modifier = Modifier.weight(1f),
                        enabled = itemizedState.items.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Submit")
                    }
                }
            }
        }
    }

    // Add item dialog
    if (showAddDialog) {
        AddItemDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, price, isFee ->
                vm.addItem(name, price, isFee)
                showAddDialog = false
            }
        )
    }

    // Edit item dialog
    editingItem?.let { item ->
        EditItemDialog(
            item = item,
            onDismiss = { editingItem = null },
            onSave = { newName, newPrice, isFee ->
                vm.editItem(item.id, newName, newPrice, isFee)
                editingItem = null
            }
        )
    }
}

@Composable
private fun ItemRow(
    item: OrderItem,
    bucket: Set<Long>,
    allParticipantIds: Set<Long>,
    participantNames: Map<Long, String>,
    onBucketChange: (Set<Long>) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showParticipants by remember { mutableStateOf(false) }

    // Build display label for current bucket
    val bucketLabel = if (bucket == allParticipantIds) "All" else {
        bucket.mapNotNull { participantNames[it] }.sorted().joinToString(", ")
    }

    Card(
        Modifier.fillMaxWidth(),
        colors = if (item.isFee) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) else CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.name,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (item.isFee) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Fee — shared by all",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        "₹%.2f".format(item.price),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                }
            }

            // Participant assignment — multi-select checkboxes
            if (!item.isFee) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { showParticipants = !showParticipants },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        bucketLabel,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        if (showParticipants) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showParticipants) "Collapse" else "Expand",
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (showParticipants) {
                    // "All" shortcut
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = bucket == allParticipantIds,
                            onCheckedChange = { checked ->
                                if (checked) onBucketChange(allParticipantIds)
                            }
                        )
                        Text("All", style = MaterialTheme.typography.bodySmall)
                    }
                    // Individual participants
                    allParticipantIds.forEach { pid ->
                        val name = participantNames[pid] ?: "Unknown"
                        val isChecked = pid in bucket
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    val newBucket = if (checked) bucket + pid else bucket - pid
                                    // Don't allow empty bucket
                                    if (newBucket.isNotEmpty()) onBucketChange(newBucket)
                                }
                            )
                            Text(name, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Shared by all",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AddItemDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Double, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var isFee by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Price (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isFee, onCheckedChange = { isFee = it })
                    Text("This is a fee/charge (shared by all)")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val price = priceText.toDoubleOrNull()
                    if (name.isNotBlank() && price != null && price > 0) {
                        onAdd(name, price, isFee)
                    }
                },
                enabled = name.isNotBlank() && (priceText.toDoubleOrNull() ?: 0.0) > 0
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditItemDialog(
    item: OrderItem,
    onDismiss: () -> Unit,
    onSave: (String, Double, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var priceText by remember { mutableStateOf("%.2f".format(item.price)) }
    var isFee by remember { mutableStateOf(item.isFee) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Price (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isFee, onCheckedChange = { isFee = it })
                    Text("This is a fee/charge (shared by all)")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val price = priceText.toDoubleOrNull()
                    if (name.isNotBlank() && price != null && price > 0) {
                        onSave(name, price, isFee)
                    }
                },
                enabled = name.isNotBlank() && (priceText.toDoubleOrNull() ?: 0.0) > 0
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


private fun createImageUri(context: Context): Uri {
    val dir = File(context.cacheDir, "receipt_images").apply { mkdirs() }
    val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}
