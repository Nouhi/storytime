package com.storytime.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.storytime.app.audio.AmbientSound
import com.storytime.app.network.ApiClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onFamilyMembersClick: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val kidName by viewModel.kidName.collectAsState()
    val kidGender by viewModel.kidGender.collectAsState()
    val readingLevel by viewModel.readingLevel.collectAsState()
    val kidPhotoPath by viewModel.kidPhotoPath.collectAsState()
    val anthropicKey by viewModel.anthropicApiKey.collectAsState()
    val googleKey by viewModel.googleAiApiKey.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val bedtimeSound by viewModel.bedtimeSound.collectAsState()
    val sleepTimerStories by viewModel.sleepTimerStories.collectAsState()
    val isOnline by viewModel.isServerOnline.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadKidPhoto(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.loadSettings()
    }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearSaveSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        TextButton(onClick = { viewModel.saveSettings() }) {
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Save success message
                if (saveSuccess) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Settings saved", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // Error message
                errorMessage?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearError() }) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss")
                            }
                        }
                    }
                }

                // Server section
                Text("Server", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { viewModel.updateServerUrl(it) },
                    label = { Text("Server URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Circle,
                                contentDescription = if (isOnline) "Online" else "Offline",
                                tint = if (isOnline) androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                else androidx.compose.ui.graphics.Color(0xFFF44336),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = { viewModel.testConnection() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Test")
                            }
                        }
                    }
                )

                HorizontalDivider()

                // Child details
                Text("Child Details", style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = kidName,
                    onValueChange = { viewModel.updateKidName(it) },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Gender picker
                Text("Gender", style = MaterialTheme.typography.bodyMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val options = listOf("" to "Not set", "boy" to "Boy", "girl" to "Girl")
                    options.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = kidGender == value,
                            onClick = { viewModel.updateKidGender(value) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size)
                        ) {
                            Text(label)
                        }
                    }
                }

                // Reading level picker
                Text("Reading Level", style = MaterialTheme.typography.bodyMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val levels = listOf(
                        "toddler" to "Toddler",
                        "early-reader" to "Early",
                        "beginner" to "Beginner",
                        "intermediate" to "Intermediate"
                    )
                    levels.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = readingLevel == value,
                            onClick = { viewModel.updateReadingLevel(value) },
                            shape = SegmentedButtonDefaults.itemShape(index, levels.size)
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Photo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (kidPhotoPath.isNotEmpty()) {
                        AsyncImage(
                            model = ApiClient.imageUrl("/api/photos/$kidPhotoPath"),
                            contentDescription = "Child photo",
                            modifier = Modifier.size(64.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    OutlinedButton(onClick = { photoPicker.launch("image/*") }) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (kidPhotoPath.isNotEmpty()) "Change Photo" else "Add Photo")
                    }
                }

                HorizontalDivider()

                // Family members
                Text("Family", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = onFamilyMembersClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.People, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Manage Family Members")
                }

                HorizontalDivider()

                // Bedtime
                Text("Bedtime", style = MaterialTheme.typography.titleMedium)

                Text("Ambient Sound", style = MaterialTheme.typography.bodyMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AmbientSound.entries.forEach { sound ->
                        FilterChip(
                            selected = bedtimeSound == sound.key,
                            onClick = { viewModel.updateBedtimeSound(sound.key) },
                            label = { Text(sound.displayName, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text("Sleep Timer", style = MaterialTheme.typography.bodyMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val timerOptions = listOf(0 to "Off", 1 to "1 Story", 2 to "2 Stories", 3 to "3 Stories")
                    timerOptions.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = sleepTimerStories == value,
                            onClick = { viewModel.updateSleepTimerStories(value) },
                            shape = SegmentedButtonDefaults.itemShape(index, timerOptions.size)
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Text(
                    "Bedtime mode dims the screen and plays soothing sounds after story time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                // API Keys
                Text("API Keys", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = anthropicKey,
                    onValueChange = { viewModel.updateAnthropicKey(it) },
                    label = { Text("Anthropic API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                OutlinedTextField(
                    value = googleKey,
                    onValueChange = { viewModel.updateGoogleKey(it) },
                    label = { Text("Google AI API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
