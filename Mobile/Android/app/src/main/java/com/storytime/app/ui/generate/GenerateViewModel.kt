package com.storytime.app.ui.generate

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.storytime.app.StorytimeApp
import com.storytime.app.model.*
import com.storytime.app.network.ApiClient
import com.storytime.app.util.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

enum class GenerationState {
    IDLE, GENERATING, COMPLETE, ERROR
}

class GenerateViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = (application as StorytimeApp).preferencesManager
    private val api = ApiClient.service

    // State machine
    private val _state = MutableStateFlow(GenerationState.IDLE)
    val state: StateFlow<GenerationState> = _state.asStateFlow()

    // Form inputs
    private val _prompt = MutableStateFlow("")
    val prompt: StateFlow<String> = _prompt.asStateFlow()

    private val _writingStyle = MutableStateFlow("standard")
    val writingStyle: StateFlow<String> = _writingStyle.asStateFlow()

    private val _imageStyle = MutableStateFlow("watercolor")
    val imageStyle: StateFlow<String> = _imageStyle.asStateFlow()

    // Styles from server
    private val _writingStyles = MutableStateFlow<List<StyleItem>>(emptyList())
    val writingStyles: StateFlow<List<StyleItem>> = _writingStyles.asStateFlow()

    private val _imageStyles = MutableStateFlow<List<StyleItem>>(emptyList())
    val imageStyles: StateFlow<List<StyleItem>> = _imageStyles.asStateFlow()

    // Generation progress
    private val _progress = MutableStateFlow(0.0)
    val progress: StateFlow<Double> = _progress.asStateFlow()

    private val _stepDetail = MutableStateFlow("")
    val stepDetail: StateFlow<String> = _stepDetail.asStateFlow()

    private val _currentStep = MutableStateFlow("")
    val currentStep: StateFlow<String> = _currentStep.asStateFlow()

    // Completed story
    private val _storyPages = MutableStateFlow<List<StoryPage>>(emptyList())
    val storyPages: StateFlow<List<StoryPage>> = _storyPages.asStateFlow()

    private val _storyId = MutableStateFlow<String?>(null)

    private val _hasImages = MutableStateFlow(true)
    val hasImages: StateFlow<Boolean> = _hasImages.asStateFlow()

    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    // Error
    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    // Family members for character picker
    private val _familyMembers = MutableStateFlow<List<com.storytime.app.model.FamilyMemberResponse>>(emptyList())
    val familyMembers: StateFlow<List<com.storytime.app.model.FamilyMemberResponse>> = _familyMembers.asStateFlow()

    private val _selectedCharacterIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedCharacterIds: StateFlow<Set<Int>> = _selectedCharacterIds.asStateFlow()

    // Whether the user has explicitly changed the selection (vs default "all")
    private val _hasCustomSelection = MutableStateFlow(false)

    // Bedtime mode
    private val _showBedtime = MutableStateFlow(false)
    val showBedtime: StateFlow<Boolean> = _showBedtime.asStateFlow()

    private val _storiesCompletedInSession = MutableStateFlow(0)

    // Toast messages for user feedback
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()
    fun clearToast() { _toastMessage.value = null }

    // Kid name
    private val _kidName = MutableStateFlow("")
    val kidName: StateFlow<String> = _kidName.asStateFlow()

    private var generationJob: Job? = null

    val suggestions = listOf(
        "A magical adventure in an enchanted forest",
        "A trip to outer space to visit friendly aliens",
        "A day at the beach with a talking dolphin",
        "A treasure hunt in a pirate ship"
    )

    val greeting: String
        get() {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val timeGreeting = when {
                hour < 12 -> "Good morning"
                hour < 17 -> "Good afternoon"
                else -> "Good evening"
            }
            val name = _kidName.value
            return if (name.isNotEmpty()) "$timeGreeting, $name!" else "Welcome to Storytime!"
        }

    init {
        viewModelScope.launch {
            prefs.kidName.collect { name ->
                _kidName.value = name
            }
        }
    }

    fun activateBedtimeMode() { _showBedtime.value = true }
    fun deactivateBedtimeMode() {
        _showBedtime.value = false
        _storiesCompletedInSession.value = 0
    }
    fun resetSleepTimerCount() { _storiesCompletedInSession.value = 0 }

    fun updatePrompt(text: String) { _prompt.value = text }
    fun updateWritingStyle(style: String) { _writingStyle.value = style }
    fun updateImageStyle(style: String) { _imageStyle.value = style }
    fun updateCurrentPage(page: Int) { _currentPage.value = page }

    fun toggleCharacter(id: Int) {
        _hasCustomSelection.value = true
        val current = _selectedCharacterIds.value
        _selectedCharacterIds.value = if (current.contains(id)) current - id else current + id
    }

    fun selectAllCharacters() {
        _hasCustomSelection.value = false
        _selectedCharacterIds.value = _familyMembers.value.map { it.id }.toSet()
    }

    fun clearCharacterSelection() {
        _hasCustomSelection.value = true
        _selectedCharacterIds.value = emptySet()
    }

    fun loadStyles() {
        viewModelScope.launch {
            try {
                val styles = api.getStyles()
                _writingStyles.value = styles.writingStyles
                _imageStyles.value = styles.imageStyles
                _writingStyle.value = styles.defaults.writingStyle
                _imageStyle.value = styles.defaults.imageStyle
            } catch (e: Exception) {
                // Use hardcoded fallbacks
                _writingStyles.value = listOf(
                    StyleItem("standard", "Classic", "📖", "Classic narrative"),
                    StyleItem("rhyming", "Rhyming", "🎵", "Dr. Seuss style"),
                    StyleItem("funny", "Funny", "😂", "Silly humor"),
                    StyleItem("bedtime", "Bedtime", "🌙", "Calm and dreamy"),
                    StyleItem("adventure", "Adventure", "⚔️", "Epic quests")
                )
                _imageStyles.value = listOf(
                    StyleItem("watercolor", "Watercolor", "🎨", "Soft, dreamy"),
                    StyleItem("cartoon", "Cartoon", "🖍️", "Bold, colorful"),
                    StyleItem("fantasy", "Fantasy", "✨", "Rich, magical"),
                    StyleItem("ghibli", "Ghibli", "🏯", "Studio Ghibli"),
                    StyleItem("none", "Text Only", "📝", "No images")
                )
            }
        }
        loadFamilyMembers()
    }

    private fun loadFamilyMembers() {
        viewModelScope.launch {
            try {
                val members = api.getFamilyMembers()
                _familyMembers.value = members
                // Default: all selected
                _selectedCharacterIds.value = members.map { it.id }.toSet()
            } catch (e: Exception) {
                // Non-critical — generate still works without explicit selection
                Log.d(TAG, "Could not load family members: ${e.message}")
            }
        }
    }

    fun generate() {
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _state.value = GenerationState.GENERATING
            _progress.value = 0.0
            _stepDetail.value = "Starting..."
            _currentStep.value = ""

            try {
                // Only send characterIds if user has made a custom selection
                val charIds = if (_hasCustomSelection.value) {
                    _selectedCharacterIds.value.toList().ifEmpty { null }
                } else null

                val response = api.startGeneration(
                    GenerateRequest(
                        prompt = _prompt.value,
                        writingStyle = _writingStyle.value,
                        imageStyle = _imageStyle.value,
                        characterIds = charIds
                    )
                )
                _storyId.value = response.storyId

                // Connect to SSE stream
                val streamUrl = ApiClient.imageUrl("/api/generate/${response.storyId}/stream")
                ApiClient.sseClient.connect(streamUrl).collect { event ->
                    handleEvent(event)
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Generation failed"
                _state.value = GenerationState.ERROR
            }
        }
    }

    private fun handleEvent(event: GenerationEvent) {
        when (event.type) {
            "progress" -> {
                _progress.value = event.progress ?: _progress.value
                _stepDetail.value = event.detail ?: _stepDetail.value
                _currentStep.value = event.step ?: _currentStep.value
            }
            "complete" -> {
                _storyPages.value = event.storyPages ?: emptyList()
                _hasImages.value = event.hasImages ?: true
                _currentPage.value = 1
                _state.value = GenerationState.COMPLETE
                _storiesCompletedInSession.value += 1
                checkSleepTimer()
            }
            "error" -> {
                _errorMessage.value = event.message ?: "Something went wrong"
                _state.value = GenerationState.ERROR
            }
        }
    }

    private fun checkSleepTimer() {
        viewModelScope.launch {
            val target = prefs.sleepTimerStories.first()
            if (target > 0 && _storiesCompletedInSession.value >= target) {
                _showBedtime.value = true
            }
        }
    }

    fun reset() {
        generationJob?.cancel()
        _state.value = GenerationState.IDLE
        _prompt.value = ""
        _progress.value = 0.0
        _stepDetail.value = ""
        _currentStep.value = ""
        _storyPages.value = emptyList()
        _storyId.value = null
        _hasImages.value = true
        _currentPage.value = 1
        _errorMessage.value = ""
        // Reset character selection to "all"
        _hasCustomSelection.value = false
        _selectedCharacterIds.value = _familyMembers.value.map { it.id }.toSet()
        // Reload family members in case they changed
        loadFamilyMembers()
    }

    fun pageImageUrl(page: Int): String? {
        val sid = _storyId.value ?: return null
        if (!_hasImages.value) return null
        return ApiClient.imageUrl("/api/generate/$sid/pages?page=$page")
    }

    fun saveEpub() {
        Log.d(TAG, "saveEpub() called, storyId=${_storyId.value}")
        val sid = _storyId.value ?: run {
            Log.e(TAG, "saveEpub: storyId is null")
            _toastMessage.value = "No story ID available"
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val url = ApiClient.imageUrl("/api/generate/$sid/epub")
                    Log.d(TAG, "saveEpub: requesting URL=$url")
                    val response = api.downloadGeneratedEpub(sid)
                    Log.d(TAG, "saveEpub: response code=${response.code()}, message=${response.message()}")
                    if (!response.isSuccessful) {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "saveEpub: HTTP error body=$errorBody")
                        return@withContext "EPUB download failed (HTTP ${response.code()})"
                    }
                    val body = response.body() ?: run {
                        Log.e(TAG, "saveEpub: response body is null")
                        return@withContext "EPUB download returned empty response"
                    }
                    val bytes = body.bytes()
                    Log.d(TAG, "saveEpub: received ${bytes.size} bytes")
                    val title = _storyPages.value.firstOrNull()?.text ?: "story"
                    val filename = "${FileHelper.sanitizeFilename(title)}.epub"
                    Log.d(TAG, "saveEpub: saving to Downloads as $filename")
                    FileHelper.saveToDownloads(getApplication(), bytes, filename, "application/epub+zip")
                } catch (e: Exception) {
                    Log.e(TAG, "saveEpub: exception", e)
                    "Failed to download EPUB: ${e.message ?: e.javaClass.simpleName}"
                }
            }
            _toastMessage.value = result
        }
    }

    fun savePdf() {
        Log.d(TAG, "savePdf() called, storyId=${_storyId.value}")
        val sid = _storyId.value ?: run {
            Log.e(TAG, "savePdf: storyId is null")
            _toastMessage.value = "No story ID available"
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val url = ApiClient.imageUrl("/api/generate/$sid/pdf")
                    Log.d(TAG, "savePdf: requesting URL=$url")
                    val response = api.downloadGeneratedPdf(sid)
                    Log.d(TAG, "savePdf: response code=${response.code()}, message=${response.message()}")
                    if (!response.isSuccessful) {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "savePdf: HTTP error body=$errorBody")
                        return@withContext "PDF download failed (HTTP ${response.code()})"
                    }
                    val body = response.body() ?: run {
                        Log.e(TAG, "savePdf: response body is null")
                        return@withContext "PDF download returned empty response"
                    }
                    val bytes = body.bytes()
                    Log.d(TAG, "savePdf: received ${bytes.size} bytes")
                    val title = _storyPages.value.firstOrNull()?.text ?: "story"
                    val filename = "${FileHelper.sanitizeFilename(title)}.pdf"
                    Log.d(TAG, "savePdf: saving to Downloads as $filename")
                    FileHelper.saveToDownloads(getApplication(), bytes, filename, "application/pdf")
                } catch (e: Exception) {
                    Log.e(TAG, "savePdf: exception", e)
                    "Failed to download PDF: ${e.message ?: e.javaClass.simpleName}"
                }
            }
            _toastMessage.value = result
        }
    }

    companion object {
        private const val TAG = "GenerateVM"
    }
}
