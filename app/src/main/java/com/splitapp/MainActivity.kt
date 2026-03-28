package com.splitapp

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.splitapp.ui.HistoryScreen
import com.splitapp.ui.SettingsScreen
import com.splitapp.ui.SplitScreen
import com.splitapp.ui.theme.SplitAppTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions granted or denied — UI handles the rest */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permissions on first launch
        val perms = buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(perms.toTypedArray())

        setContent {
            SplitAppTheme {
                AppNavigation()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("SplitApp") })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0; navController.navigate("history") },
                    icon = { Icon(imageVector = Icons.Default.History, contentDescription = null) },
                    label = { Text("Transactions") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1; navController.navigate("settings") },
                    icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "history",
            modifier = Modifier.padding(padding)
        ) {
            composable("history") {
                HistoryScreen(
                    onTransactionClick = { id -> navController.navigate("split/$id") }
                )
            }
            composable("settings") {
                SettingsScreen()
            }
            composable("split/{transactionId}") { backStack ->
                val id = backStack.arguments?.getString("transactionId")?.toLongOrNull() ?: return@composable
                SplitScreen(
                    transactionId = id,
                    onDone = { navController.popBackStack() }
                )
            }
        }
    }
}
