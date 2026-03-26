package com.splitapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import com.splitapp.receiver.SmsReceiver
import com.splitapp.ui.SplitScreen
import com.splitapp.ui.theme.SplitAppTheme

/**
 * Launched from a notification tap. Shows the split screen for a specific transaction.
 */
@OptIn(ExperimentalMaterial3Api::class)
class SplitActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val transactionId = intent.getLongExtra(SmsReceiver.EXTRA_TRANSACTION_ID, -1L)
        if (transactionId == -1L) { finish(); return }

        setContent {
            SplitAppTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Split Transaction") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    }
                ) { padding ->
                    SplitScreen(
                        transactionId = transactionId,
                        onDone = { finish() },
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }
    }
}
