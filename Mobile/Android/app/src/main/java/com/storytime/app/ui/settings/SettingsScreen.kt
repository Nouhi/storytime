package com.storytime.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.storytime.app.audio.AmbientSound
import com.storytime.app.network.ApiClient
import com.storytime.app.ui.theme.*

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

    var showDeveloperOptions by remember { mutableStateOf(false) }

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
                            strokeWidth = 2.dp,
                            color = Purple500
                        )
                    } else {
                        TextButton(onClick = { viewModel.saveSettings() }) {
                            Text("Save", fontWeight = FontWeight.SemiBold, color = Purple500)
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
                CircularProgressIndicator(color = Purple500)
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
                            containerColor = Teal500.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = Teal500)
                            Spacer(Modifier.width(8.dp))
                            Text("Settings saved", style = MaterialTheme.typography.bodyMedium, color = Teal500)
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
                Text("Server", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                // Status badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Circle,
                        contentDescription = null,
                        tint = if (isOnline) Teal500 else Red500,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        if (isOnline) "Connected" else "Not connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isOnline) Teal500 else Red500
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { viewModel.testConnection() }) {
                        Text("Test", color = Purple500)
                    }
                }

                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { viewModel.updateServerUrl(it) },
                    label = { Text("Server URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                )

                HorizontalDivider()

                // Child details
                Text("Child Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                // Photo — centered and prominent
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (kidPhotoPath.isNotEmpty()) {
                        AsyncImage(
                            model = ApiClient.imageUrl("/api/photos/$kidPhotoPath"),
                            contentDescription = "Child photo",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Purple80),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = Purple500.copy(alpha = 0.5f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    TextButton(onClick = { photoPicker.launch("image/*") }) {
                        Text(
                            if (kidPhotoPath.isNotEmpty()) "Change Photo" else "Add Photo",
                            color = Purple500
                        )
                    }
                }

                OutlinedTextField(
                    value = kidName,
                    onValueChange = { viewModel.updateKidName(it) },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Gender picker
                Text("Gender", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
                Text("Reading Level", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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

                HorizontalDivider()

                // Family members
                Text("Family", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedButton(
                    onClick = onFamilyMembersClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.People, contentDescription = null, tint = Purple500)
                    Spacer(Modifier.width(8.dp))
                    Text("Manage Family Members")
                }

                HorizontalDivider()

                // Bedtime
                Text("Bedtime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                Text("Ambient Sound", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AmbientSound.entries.size) { index ->
                        val sound = AmbientSound.entries[index]
                        FilterChip(
                            selected = bedtimeSound == sound.key,
                            onClick = { viewModel.updateBedtimeSound(sound.key) },
                            label = { Text(sound.displayName, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = SurfaceCard,
                                selectedContainerColor = Purple80,
                                selectedLabelColor = Purple700
                            )
                        )
                    }
                }

                Text("Sleep Timer", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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

                // Developer Options (collapsed by default)
                Surface(
                    onClick = { showDeveloperOptions = !showDeveloperOptions },
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceCard
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Developer Options",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            if (showDeveloperOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedVisibility(visible = showDeveloperOptions) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = anthropicKey,
                            onValueChange = { viewModel.updateAnthropicKey(it) },
                            label = { Text("Anthropic API Key") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = googleKey,
                            onValueChange = { viewModel.updateGoogleKey(it) },
                            label = { Text("Google AI API Key") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Text(
                            "Keys are stored on the server. Masked keys indicate an existing key is set.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
