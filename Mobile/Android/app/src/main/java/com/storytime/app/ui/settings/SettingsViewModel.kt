package com.storytime.app.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.storytime.app.StorytimeApp
import com.storytime.app.model.SettingsUpdateRequest
import com.storytime.app.network.ApiClient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = (application as StorytimeApp).preferencesManager
    private val api = ApiClient.service

    private val _kidName = MutableStateFlow("")
    val kidName: StateFlow<String> = _kidName.asStateFlow()

    private val _kidGender = MutableStateFlow("")
    val kidGender: StateFlow<String> = _kidGender.asStateFlow()

    private val _readingLevel = MutableStateFlow("early-reader")
    val readingLevel: StateFlow<String> = _readingLevel.asStateFlow()

    private val _kidPhotoPath = MutableStateFlow("")
    val kidPhotoPath: StateFlow<String> = _kidPhotoPath.asStateFlow()

    private val _anthropicApiKey = MutableStateFlow("")
    val anthropicApiKey: StateFlow<String> = _anthropicApiKey.asStateFlow()

    private val _googleAiApiKey = MutableStateFlow("")
    val googleAiApiKey: StateFlow<String> = _googleAiApiKey.asStateFlow()

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _isServerOnline = MutableStateFlow(false)
    val isServerOnline: StateFlow<Boolean> = _isServerOnline.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    private val _bedtimeSound = MutableStateFlow("whiteNoise")
    val bedtimeSound: StateFlow<String> = _bedtimeSound.asStateFlow()

    private val _sleepTimerStories = MutableStateFlow(0)
    val sleepTimerStories: StateFlow<Int> = _sleepTimerStories.asStateFlow()

    init {
        viewModelScope.launch {
            _serverUrl.value = prefs.serverUrl.first()
        }
        viewModelScope.launch {
            prefs.bedtimeSound.collect { _bedtimeSound.value = it }
        }
        viewModelScope.launch {
            prefs.sleepTimerStories.collect { _sleepTimerStories.value = it }
        }
    }

    fun updateKidName(name: String) { _kidName.value = name }
    fun updateKidGender(gender: String) { _kidGender.value = gender }
    fun updateReadingLevel(level: String) { _readingLevel.value = level }
    fun updateAnthropicKey(key: String) { _anthropicApiKey.value = key }
    fun updateGoogleKey(key: String) { _googleAiApiKey.value = key }
    fun updateServerUrl(url: String) { _serverUrl.value = url }
    fun clearError() { _errorMessage.value = null }
    fun clearSaveSuccess() { _saveSuccess.value = false }

    fun updateBedtimeSound(sound: String) {
        _bedtimeSound.value = sound
        viewModelScope.launch { prefs.setBedtimeSound(sound) }
    }

    fun updateSleepTimerStories(count: Int) {
        _sleepTimerStories.value = count
        viewModelScope.launch { prefs.setSleepTimerStories(count) }
    }

    fun loadSettings() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val settings = api.getSettings()
                _kidName.value = settings.kidName
                _kidGender.value = settings.kidGender
                _readingLevel.value = settings.readingLevel
                _kidPhotoPath.value = settings.kidPhotoPath
                _anthropicApiKey.value = settings.anthropicApiKey
                _googleAiApiKey.value = settings.googleAiApiKey

                // Persist kid name locally
                prefs.setKidName(settings.kidName)

                _isServerOnline.value = true
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load settings: ${e.message}"
                _isServerOnline.value = false
            }
            _isLoading.value = false
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            _isSaving.value = true
            _errorMessage.value = null
            try {
                // Save server URL to DataStore
                prefs.setServerUrl(_serverUrl.value)

                val request = SettingsUpdateRequest(
                    kidName = _kidName.value,
                    kidGender = _kidGender.value,
                    readingLevel = _readingLevel.value,
                    anthropicApiKey = _anthropicApiKey.value.takeIf { !it.contains("*") },
                    googleAiApiKey = _googleAiApiKey.value.takeIf { !it.contains("*") }
                )
                api.updateSettings(request)

                // Persist kid name locally
                prefs.setKidName(_kidName.value)
                _saveSuccess.value = true
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save: ${e.message}"
            }
            _isSaving.value = false
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            try {
                prefs.setServerUrl(_serverUrl.value)
                api.checkHealth()
                _isServerOnline.value = true
            } catch (e: Exception) {
                _isServerOnline.value = false
            }
        }
    }

    fun uploadKidPhoto(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<StorytimeApp>()
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val bytes = inputStream.readBytes()
                inputStream.close()

                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val ext = if (mimeType.contains("png")) "png" else "jpg"

                val requestBody = bytes.toRequestBody(mimeType.toMediaType())
                val part = MultipartBody.Part.createFormData("file", "photo.$ext", requestBody)
                val typeBody = "kid".toRequestBody("text/plain".toMediaType())

                val response = api.uploadPhoto(file = part, type = typeBody)
                _kidPhotoPath.value = response.photoPath
            } catch (e: Exception) {
                _errorMessage.value = "Photo upload failed: ${e.message}"
            }
        }
    }
}
