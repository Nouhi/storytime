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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.storytime.app.R
import com.storytime.app.audio.AmbientSound
import com.storytime.app.network.ApiClient
import com.storytime.app.ui.generate.GenerateViewModel
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
    val language by viewModel.language.collectAsState()
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
                title = { Text(stringResource(R.string.tab_settings)) },
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = Purple500
                        )
                    } else {
                        TextButton(onClick = { viewModel.saveSettings() }) {
                            Text(stringResource(R.string.button_save), fontWeight = FontWeight.SemiBold, color = Purple500)
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
                            Text(stringResource(R.string.settings_saved), style = MaterialTheme.typography.bodyMedium, color = Teal500)
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
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    }
                }

                // Server section
                Text(stringResource(R.string.settings_server), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

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
                        if (isOnline) stringResource(R.string.settings_connected) else stringResource(R.string.settings_not_connected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isOnline) Teal500 else Red500
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { viewModel.testConnection() }) {
                        Text(stringResource(R.string.settings_test), color = Purple500)
                    }
                }

                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { viewModel.updateServerUrl(it) },
                    label = { Text(stringResource(R.string.settings_server_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                )

                HorizontalDivider()

                // Language
                Text(stringResource(R.string.section_language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                var showLanguageMenu by remember { mutableStateOf(false) }
                val languages = GenerateViewModel.FALLBACK_LANGUAGES
                val currentLang = languages.find { it.id == language } ?: languages.first()

                Box {
                    Surface(
                        onClick = { showLanguageMenu = true },
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceCard
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(currentLang.emoji, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                currentLang.description,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showLanguageMenu,
                        onDismissRequest = { showLanguageMenu = false }
                    ) {
                        languages.forEach { lang ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(lang.emoji)
                                        Spacer(Modifier.width(8.dp))
                                        Text(lang.description)
                                    }
                                },
                                onClick = {
                                    viewModel.updateLanguage(lang.id)
                                    showLanguageMenu = false
                                },
                                trailingIcon = if (lang.id == language) {
                                    { Icon(Icons.Default.Check, contentDescription = null, tint = Purple500) }
                                } else null
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Child details
                Text(stringResource(R.string.settings_child_details), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                // Photo — centered and prominent
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (kidPhotoPath.isNotEmpty()) {
                        AsyncImage(
                            model = ApiClient.imageUrl("/api/photos/$kidPhotoPath"),
                            contentDescription = null,
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
                            if (kidPhotoPath.isNotEmpty()) stringResource(R.string.settings_change_photo) else stringResource(R.string.settings_add_photo),
                            color = Purple500
                        )
                    }
                }

                OutlinedTextField(
                    value = kidName,
                    onValueChange = { viewModel.updateKidName(it) },
                    label = { Text(stringResource(R.string.settings_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Gender picker
                Text(stringResource(R.string.settings_gender), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val options = listOf(
                        "" to stringResource(R.string.settings_gender_not_set),
                        "boy" to stringResource(R.string.settings_gender_boy),
                        "girl" to stringResource(R.string.settings_gender_girl)
                    )
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
                Text(stringResource(R.string.settings_reading_level), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val levels = listOf(
                        "toddler" to stringResource(R.string.settings_reading_toddler),
                        "early-reader" to stringResource(R.string.settings_reading_early),
                        "beginner" to stringResource(R.string.settings_reading_beginner),
                        "intermediate" to stringResource(R.string.settings_reading_intermediate)
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
                Text(stringResource(R.string.settings_family), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedButton(
                    onClick = onFamilyMembersClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.People, contentDescription = null, tint = Purple500)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_manage_family))
                }

                HorizontalDivider()

                // Bedtime
                Text(stringResource(R.string.settings_bedtime), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                Text(stringResource(R.string.settings_ambient_sound), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AmbientSound.entries.size) { index ->
                        val sound = AmbientSound.entries[index]
                        val soundLabel = ambientSoundLabel(sound)
                        FilterChip(
                            selected = bedtimeSound == sound.key,
                            onClick = { viewModel.updateBedtimeSound(sound.key) },
                            label = { Text(soundLabel, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = SurfaceCard,
                                selectedContainerColor = Purple80,
                                selectedLabelColor = Purple700
                            )
                        )
                    }
                }

                Text(stringResource(R.string.settings_sleep_timer), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val timerOptions = listOf(
                        0 to stringResource(R.string.settings_sleep_off),
                        1 to stringResource(R.string.settings_sleep_1),
                        2 to stringResource(R.string.settings_sleep_2),
                        3 to stringResource(R.string.settings_sleep_3)
                    )
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
                    stringResource(R.string.settings_bedtime_footer),
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
                            stringResource(R.string.settings_developer_options),
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
                            label = { Text(stringResource(R.string.settings_anthropic_key)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = googleKey,
                            onValueChange = { viewModel.updateGoogleKey(it) },
                            label = { Text(stringResource(R.string.settings_google_key)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Text(
                            stringResource(R.string.settings_keys_footer),
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

@Composable
private fun ambientSoundLabel(sound: AmbientSound): String {
    return when (sound) {
        AmbientSound.WHITE_NOISE -> stringResource(R.string.sound_whiteNoise)
        AmbientSound.BROWN_NOISE -> stringResource(R.string.sound_brownNoise)
        AmbientSound.PINK_NOISE -> stringResource(R.string.sound_pinkNoise)
        AmbientSound.SOFT_RAIN -> stringResource(R.string.sound_softRain)
        AmbientSound.OCEAN_WAVES -> stringResource(R.string.sound_oceanWaves)
        AmbientSound.CRACKLING_FIREPLACE -> stringResource(R.string.sound_cracklingFireplace)
        AmbientSound.FOREST_NIGHT -> stringResource(R.string.sound_forestNight)
        AmbientSound.HEARTBEAT -> stringResource(R.string.sound_heartbeat)
    }
}
