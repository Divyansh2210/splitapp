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

    // Generate bucket options from participants
    val bucketOptions = remember(itemizedState.allParticipantIds, itemizedState.participantNames) {
        generateBucketOptions(itemizedState.allParticipantIds, itemizedState.participantNames)
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
                    bucketOptions = bucketOptions,
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
                        onClick = { vm.submitItemizedSplit(transaction, groupId) },
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
            onSave = { newName, newPrice ->
                vm.editItem(item.id, newName, newPrice)
                editingItem = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemRow(
    item: OrderItem,
    bucket: Set<Long>,
    bucketOptions: List<Pair<String, Set<Long>>>,
    onBucketChange: (Set<Long>) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // Find current bucket label
    val currentLabel = bucketOptions.find { it.second == bucket }?.first ?: "Custom"

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
                if (!item.isFee) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                }
            }

            // Bucket dropdown
            if (!item.isFee) {
                Spacer(Modifier.height(4.dp))
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = currentLabel,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        label = { Text("Split between") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        bucketOptions.forEach { (label, ids) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onBucketChange(ids)
                                    expanded = false
                                }
                            )
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
    onSave: (String, Double) -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var priceText by remember { mutableStateOf("%.2f".format(item.price)) }

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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val price = priceText.toDoubleOrNull()
                    if (name.isNotBlank() && price != null && price > 0) {
                        onSave(name, price)
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

/**
 * Generate all non-empty subsets of participants as dropdown options.
 * Sorted by size descending ("All" first, then pairs, then singles).
 */
fun generateBucketOptions(
    participantIds: Set<Long>,
    names: Map<Long, String>
): List<Pair<String, Set<Long>>> {
    val ids = participantIds.toList()
    val n = ids.size
    if (n == 0) return emptyList()

    val options = mutableListOf<Pair<String, Set<Long>>>()

    // Generate all non-empty subsets via bitmask
    for (mask in 1 until (1 shl n)) {
        val subset = mutableSetOf<Long>()
        for (i in 0 until n) {
            if (mask and (1 shl i) != 0) {
                subset.add(ids[i])
            }
        }
        val label = if (subset.size == n) {
            "All"
        } else {
            subset.mapNotNull { names[it] }.sorted().joinToString(" + ")
        }
        options.add(Pair(label, subset))
    }

    // Sort: largest subsets first, then alphabetical
    return options.sortedWith(compareByDescending<Pair<String, Set<Long>>> { it.second.size }.thenBy { it.first })
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
