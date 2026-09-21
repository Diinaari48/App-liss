package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SupabaseConfigManager

@Composable
fun SupabaseSetupScreen(
    initialUrl: String = "",
    initialKey: String = "",
    onSave: (url: String, key: String) -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    var key by remember { mutableStateOf(initialKey) }
    var showKey by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isUrlValid = SupabaseConfigManager.isValidHttpsUrl(url)
    val isKeyValid = !SupabaseConfigManager.isPlaceholderKey(key) && key.trim().isNotBlank()
    val canSubmit = isUrlValid && isKeyValid

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header Icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudQueue,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Text(
                text = "Supabase Setup",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Configure your Supabase project credentials to connect this device for commands and telemetry.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Guidance Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Find these under Project Settings > API in your Supabase dashboard. Configuration is stored locally on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Supabase URL Input
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    errorMessage = null
                },
                label = { Text("Supabase URL") },
                placeholder = { Text("https://your-project.supabase.co") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (url.isNotBlank()) {
                        Icon(
                            imageVector = if (isUrlValid) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = if (isUrlValid) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                        )
                    }
                },
                isError = url.isNotBlank() && !isUrlValid,
                supportingText = {
                    if (url.isNotBlank() && !isUrlValid) {
                        Text(
                            text = "Must be a valid https:// URL (e.g. https://xyz.supabase.co)",
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text("Project REST/Realtime endpoint URL")
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_supabase_url_input")
            )

            // Supabase Anon Key Input
            OutlinedTextField(
                value = key,
                onValueChange = {
                    key = it
                    errorMessage = null
                },
                label = { Text("Supabase Anon / Public Key") },
                placeholder = { Text("eyJhbGciOiJIUzI1NiIsInR5cCI6...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showKey) "Hide key" else "Show key"
                        )
                    }
                },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                isError = key.isNotBlank() && !isKeyValid,
                supportingText = {
                    if (key.isNotBlank() && !isKeyValid) {
                        Text(
                            text = "Please enter a valid Anon Key (cannot be placeholder)",
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text("Anon/public API key from Supabase Dashboard")
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_supabase_key_input")
            )

            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save & Continue Button
            Button(
                onClick = {
                    val cleanUrl = url.trim()
                    val cleanKey = key.trim()
                    if (!SupabaseConfigManager.isValidHttpsUrl(cleanUrl)) {
                        errorMessage = "Please enter a valid HTTPS Supabase URL (e.g. https://xyz.supabase.co)."
                        return@Button
                    }
                    if (SupabaseConfigManager.isPlaceholderKey(cleanKey)) {
                        errorMessage = "Please enter a valid anon key."
                        return@Button
                    }
                    onSave(cleanUrl, cleanKey)
                },
                enabled = canSubmit,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("setup_save_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Save & Continue",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SupabaseSettingsDialog(
    initialUrl: String,
    initialKey: String,
    onDismiss: () -> Unit,
    onSave: (url: String, key: String) -> Unit,
    onClear: () -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    var key by remember { mutableStateOf(initialKey) }
    var showKey by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val isUrlValid = SupabaseConfigManager.isValidHttpsUrl(url)
    val isKeyValid = !SupabaseConfigManager.isPlaceholderKey(key) && key.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudQueue,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Supabase Settings")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Update the Supabase project URL and anon key used by this device without rebuilding or reinstalling.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        errorText = null
                    },
                    label = { Text("Supabase URL") },
                    placeholder = { Text("https://your-project.supabase.co") },
                    singleLine = true,
                    isError = url.isNotBlank() && !isUrlValid,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_supabase_url_input")
                )

                OutlinedTextField(
                    value = key,
                    onValueChange = {
                        key = it
                        errorText = null
                    },
                    label = { Text("Supabase Anon Key") },
                    placeholder = { Text("eyJhbGci...") },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showKey) "Hide key" else "Show key"
                            )
                        }
                    },
                    isError = key.isNotBlank() && !isKeyValid,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_supabase_key_input")
                )

                if (errorText != null) {
                    Text(
                        text = errorText ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanUrl = url.trim()
                    val cleanKey = key.trim()
                    if (!SupabaseConfigManager.isValidHttpsUrl(cleanUrl)) {
                        errorText = "Please enter a valid HTTPS URL (https://...)."
                        return@Button
                    }
                    if (SupabaseConfigManager.isPlaceholderKey(cleanKey)) {
                        errorText = "Please enter a valid Anon Key."
                        return@Button
                    }
                    onSave(cleanUrl, cleanKey)
                },
                enabled = isUrlValid && isKeyValid,
                modifier = Modifier.testTag("dialog_save_button")
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.testTag("dialog_clear_button")
                ) {
                    Text("Clear Config", color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
