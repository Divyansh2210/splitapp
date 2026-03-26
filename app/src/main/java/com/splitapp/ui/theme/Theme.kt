package com.splitapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun SplitAppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = try {
        dynamicLightColorScheme(context)
    } catch (e: Exception) {
        lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
