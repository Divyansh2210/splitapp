package com.splitapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.splitapp.viewmodel.SettingsUiState
import com.splitapp.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val savedApiKey by vm.apiKey.collectAsStateWithLifecycle(initialValue = "")
    val savedGeminiKey by vm.geminiApiKey.collectAsStateWithLifecycle(initialValue = "")
    val syncState by vm.syncState.collectAsStateWithLifecycle()

    var apiKeyInput by remember(savedApiKey) { mutableStateOf(savedApiKey) }
    var showKey by remember { mutableStateOf(false) }
    var geminiKeyInput by remember(savedGeminiKey) { mutableStateOf(savedGeminiKey) }
    var showGeminiKey by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        // API Key
        Text("Splitwise API Key", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API Key") },
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    val icon: ImageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility
                    Icon(
                        imageVector = icon,
                        contentDescription = if (showKey) "Hide" else "Show"
                    )
                }
            },
            singleLine = true
        )
        Button(
            onClick = { vm.saveApiKey(apiKeyInput) },
            enabled = apiKeyInput.isNotBlank() && apiKeyInput != savedApiKey,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save API Key") }

        HorizontalDivider()

        // Gemini API Key
        Text("Gemini API Key", style = MaterialTheme.typography.titleMedium)
        Text(
            "For receipt scanning (itemized split)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = geminiKeyInput,
            onValueChange = { geminiKeyInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API Key") },
            visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                    val icon: ImageVector = if (showGeminiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility
                    Icon(
                        imageVector = icon,
                        contentDescription = if (showGeminiKey) "Hide" else "Show"
                    )
                }
            },
            singleLine = true
        )
        Button(
            onClick = { vm.saveGeminiApiKey(geminiKeyInput) },
            enabled = geminiKeyInput.isNotBlank() && geminiKeyInput != savedGeminiKey,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save Gemini Key") }

        HorizontalDivider()

        // Sync friends
        Text("Splitwise Friends", style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = { vm.syncFriendsAndGroups() },
            enabled = savedApiKey.isNotBlank() && syncState !is SettingsUiState.SyncInProgress,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (syncState is SettingsUiState.SyncInProgress) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("Sync Friends & Groups from Splitwise")
        }

        when (val s = syncState) {
            is SettingsUiState.SyncSuccess ->
                Text(
                    "✓ Synced ${s.friendCount} friends and ${s.groupCount} groups",
                    color = MaterialTheme.colorScheme.tertiary
                )
            is SettingsUiState.SyncError ->
                Text(s.message, color = MaterialTheme.colorScheme.error)
            else -> {}
        }
    }
}
