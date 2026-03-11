package com.storytime.app.ui.generate

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.storytime.app.R
import com.storytime.app.StorytimeApp
import com.storytime.app.model.*
import com.storytime.app.network.ApiClient
import com.storytime.app.util.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.HttpException
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

    private val _lesson = MutableStateFlow("none")
    val lesson: StateFlow<String> = _lesson.asStateFlow()

    private val _customWritingStyle = MutableStateFlow("")
    val customWritingStyle: StateFlow<String> = _customWritingStyle.asStateFlow()

    private val _customImageStyle = MutableStateFlow("")
    val customImageStyle: StateFlow<String> = _customImageStyle.asStateFlow()

    private val _customLesson = MutableStateFlow("")
    val customLesson: StateFlow<String> = _customLesson.asStateFlow()

    private val _language = MutableStateFlow("en")
    val language: StateFlow<String> = _language.asStateFlow()

    // Styles from server
    private val _writingStyles = MutableStateFlow<List<StyleItem>>(emptyList())
    val writingStyles: StateFlow<List<StyleItem>> = _writingStyles.asStateFlow()

    private val _imageStyles = MutableStateFlow<List<StyleItem>>(emptyList())
    val imageStyles: StateFlow<List<StyleItem>> = _imageStyles.asStateFlow()

    private val _lessons = MutableStateFlow<List<StyleItem>>(emptyList())
    val lessons: StateFlow<List<StyleItem>> = _lessons.asStateFlow()

    private val _languages = MutableStateFlow<List<StyleItem>>(emptyList())
    val languages: StateFlow<List<StyleItem>> = _languages.asStateFlow()

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

    // Bedtime story toggle
    private val _isBedtimeStory = MutableStateFlow(false)
    val isBedtimeStory: StateFlow<Boolean> = _isBedtimeStory.asStateFlow()

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

    val suggestions: List<String>
        get() {
            val ctx = getApplication<Application>()
            return listOf(
                ctx.getString(R.string.suggestion_1),
                ctx.getString(R.string.suggestion_2),
                ctx.getString(R.string.suggestion_3),
                ctx.getString(R.string.suggestion_4)
            )
        }

    val greeting: String
        get() {
            val ctx = getApplication<Application>()
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val timeGreeting = when {
                hour < 12 -> ctx.getString(R.string.greeting_morning)
                hour < 17 -> ctx.getString(R.string.greeting_afternoon)
                else -> ctx.getString(R.string.greeting_evening)
            }
            val name = _kidName.value
            return if (name.isNotEmpty()) ctx.getString(R.string.greeting_personalized, timeGreeting, name) else ctx.getString(R.string.greeting_welcome)
        }

    init {
        viewModelScope.launch {
            prefs.kidName.collect { name ->
                _kidName.value = name
            }
        }
        viewModelScope.launch {
            prefs.language.collect { lang ->
                _language.value = lang
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
    fun updateLesson(lesson: String) { _lesson.value = lesson }
    fun updateCustomWritingStyle(text: String) { _customWritingStyle.value = text }
    fun updateCustomImageStyle(text: String) { _customImageStyle.value = text }
    fun updateCustomLesson(text: String) { _customLesson.value = text }
    fun updateCurrentPage(page: Int) { _currentPage.value = page }
    fun updateLanguage(lang: String) {
        _language.value = lang
        viewModelScope.launch {
            prefs.setLanguage(lang)
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
    }
    fun updateBedtimeStory(value: Boolean) { _isBedtimeStory.value = value }

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
                _writingStyles.value = localizeItems(styles.writingStyles, "style")
                _imageStyles.value = localizeItems(styles.imageStyles, "style")
                _lessons.value = localizeItems(styles.lessons ?: emptyList(), "lesson")
                _languages.value = styles.languages ?: FALLBACK_LANGUAGES
                _writingStyle.value = styles.defaults.writingStyle
                _imageStyle.value = styles.defaults.imageStyle
                _lesson.value = styles.defaults.lesson ?: "none"
                _language.value = styles.defaults.language ?: "en"
            } catch (e: Exception) {
                // Use hardcoded fallbacks with localized labels
                _writingStyles.value = localizeItems(listOf(
                    StyleItem("standard", "Standard", "\uD83D\uDCD6", "Classic bedtime story narration"),
                    StyleItem("rhyming", "Rhyming", "\uD83C\uDFB5", "Dr. Seuss-style rhyming verse"),
                    StyleItem("funny", "Funny", "\uD83D\uDE02", "Silly humor and unexpected twists"),
                    StyleItem("sound-effects", "Sound Effects", "\uD83D\uDCA5", "Onomatopoeia and interactive sounds"),
                    StyleItem("repetitive", "Repetitive", "\uD83D\uDD01", "Cumulative story with repeating phrases"),
                    StyleItem("bedtime", "Bedtime", "\uD83C\uDF19", "Extra calm and dreamy for sleepy time"),
                    StyleItem("adventure", "Adventure", "⚔\uFE0F", "Epic quests and brave heroes")
                ), "style")
                _imageStyles.value = localizeItems(listOf(
                    StyleItem("watercolor", "Watercolor", "\uD83C\uDFA8", "Soft, dreamy watercolor paintings"),
                    StyleItem("fantasy", "Fantasy", "\uD83E\uDDD9", "Rich, magical fantasy art"),
                    StyleItem("realistic", "Realistic", "\uD83D\uDCF7", "Photo-realistic digital art"),
                    StyleItem("cartoon", "Cartoon", "\uD83E\uDDB8", "Bold, colorful cartoon style"),
                    StyleItem("classic-storybook", "Classic Storybook", "\uD83D\uDCDA", "Vintage children's book illustrations"),
                    StyleItem("anime", "Anime", "✨", "Japanese anime-inspired art"),
                    StyleItem("ghibli", "Ghibli", "\uD83C\uDFD4\uFE0F", "Studio Ghibli-inspired art"),
                    StyleItem("chibi", "Chibi", "\uD83C\uDF80", "Cute, super-deformed chibi art"),
                    StyleItem("papercraft", "Papercraft", "✂\uFE0F", "Cut-paper collage style"),
                    StyleItem("pixel", "Pixel Art", "\uD83D\uDC7E", "Retro pixel art style"),
                    StyleItem("minimalist", "Minimalist", "⚪", "Clean, simple geometric shapes"),
                    StyleItem("crayon", "Crayon", "\uD83D\uDD8D\uFE0F", "Child-like crayon and colored pencil"),
                    StyleItem("pop-art", "Pop Art", "\uD83C\uDFAA", "Bold pop art with halftone dots"),
                    StyleItem("oil-painting", "Oil Painting", "\uD83D\uDDBC\uFE0F", "Rich, textured oil painting style"),
                    StyleItem("none", "No Images", "\uD83D\uDCDD", "Text-only story, no illustrations")
                ), "style")
                _lessons.value = localizeItems(listOf(
                    StyleItem("none", "None", "\uD83D\uDCD6", "No specific lesson — just a fun story"),
                    StyleItem("sharing", "Sharing", "\uD83E\uDD1D", "Learning to share with others"),
                    StyleItem("bravery", "Bravery", "\uD83E\uDD81", "Finding courage in tough moments"),
                    StyleItem("kindness", "Kindness", "\uD83D\uDC9B", "Being kind and caring to others"),
                    StyleItem("patience", "Patience", "\uD83D\uDC22", "Learning to wait and be patient"),
                    StyleItem("honesty", "Honesty", "⭐", "The importance of telling the truth"),
                    StyleItem("gratitude", "Gratitude", "\uD83D\uDE4F", "Appreciating what you have"),
                    StyleItem("teamwork", "Teamwork", "\uD83E\uDDE9", "Working together to achieve goals"),
                    StyleItem("empathy", "Empathy", "\uD83E\uDEC2", "Understanding others' feelings"),
                    StyleItem("perseverance", "Perseverance", "\uD83C\uDFD4\uFE0F", "Not giving up when things are hard")
                ), "lesson")
                _languages.value = FALLBACK_LANGUAGES
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

    private fun validateCustomInputs(): String? {
        val ctx = getApplication<Application>()
        if (_writingStyle.value == "custom") {
            val text = _customWritingStyle.value.trim()
            if (text.isEmpty()) return ctx.getString(R.string.validation_custom_writing_empty)
            if (text.length > 500) return ctx.getString(R.string.validation_custom_writing_long)
        }
        if (_imageStyle.value == "custom") {
            val text = _customImageStyle.value.trim()
            if (text.isEmpty()) return ctx.getString(R.string.validation_custom_image_empty)
            if (text.length > 500) return ctx.getString(R.string.validation_custom_image_long)
        }
        if (_lesson.value == "custom") {
            val text = _customLesson.value.trim()
            if (text.isEmpty()) return ctx.getString(R.string.validation_custom_lesson_empty)
            if (text.length > 500) return ctx.getString(R.string.validation_custom_lesson_long)
        }
        return null
    }

    fun generate() {
        generationJob?.cancel()

        val validationError = validateCustomInputs()
        if (validationError != null) {
            _errorMessage.value = validationError
            _state.value = GenerationState.ERROR
            return
        }

        val ctx = getApplication<Application>()

        generationJob = viewModelScope.launch {
            _state.value = GenerationState.GENERATING
            _progress.value = 0.0
            _stepDetail.value = ctx.getString(R.string.progress_starting)
            _currentStep.value = ""

            try {
                // Only send characterIds if user has made a custom selection
                val charIds = if (_hasCustomSelection.value) {
                    _selectedCharacterIds.value.toList().ifEmpty { null }
                } else null

                val lessonValue = _lesson.value.let { if (it == "none") null else it }
                val response = api.startGeneration(
                    GenerateRequest(
                        prompt = _prompt.value,
                        writingStyle = _writingStyle.value,
                        imageStyle = _imageStyle.value,
                        lesson = lessonValue,
                        characterIds = charIds,
                        customWritingStyle = if (_writingStyle.value == "custom") _customWritingStyle.value else null,
                        customImageStyle = if (_imageStyle.value == "custom") _customImageStyle.value else null,
                        customLesson = if (_lesson.value == "custom") _customLesson.value else null,
                        bedtimeStory = if (_isBedtimeStory.value) true else null,
                        language = if (_language.value != "en") _language.value else null
                    )
                )
                _storyId.value = response.storyId

                // Connect to SSE stream
                val streamUrl = ApiClient.imageUrl("/api/generate/${response.storyId}/stream")
                ApiClient.sseClient.connect(streamUrl).collect { event ->
                    handleEvent(event)
                }
            } catch (e: HttpException) {
                // Parse the JSON error body from server (e.g. semantic validation errors)
                val errorBody = try {
                    val body = e.response()?.errorBody()?.string()
                    if (body != null) JSONObject(body).optString("error", "") else ""
                } catch (_: Exception) { "" }
                _errorMessage.value = errorBody.ifEmpty { "Server error (HTTP ${e.code()})" }
                _state.value = GenerationState.ERROR
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
                val ctx = getApplication<Application>()
                _stepDetail.value = when (event.step) {
                    "generating-story" -> ctx.getString(R.string.progress_writing)
                    "generating-images" -> ctx.getString(R.string.progress_painting)
                    "assembling-ebook" -> ctx.getString(R.string.progress_assembling)
                    else -> event.detail ?: ctx.getString(R.string.progress_starting)
                }
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
                val ctx = getApplication<Application>()
                _errorMessage.value = event.message ?: ctx.getString(R.string.error_title)
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

    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
        _state.value = GenerationState.IDLE
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
        _isBedtimeStory.value = false
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

    /**
     * Localize style/lesson items by mapping their ID to a string resource.
     * Looks up "style_<id>" / "style_desc_<id>" for styles, "lesson_<id>" / "lesson_desc_<id>" for lessons.
     */
    private fun localizeItems(items: List<StyleItem>, keyPrefix: String): List<StyleItem> {
        val ctx = getApplication<Application>()
        val resources = ctx.resources
        val packageName = ctx.packageName
        return items.map { item ->
            val idKey = item.id.replace("-", "_")
            val labelKey = "${keyPrefix}_$idKey"
            val descKey = "${keyPrefix}_desc_$idKey"
            val labelResId = resources.getIdentifier(labelKey, "string", packageName)
            val descResId = resources.getIdentifier(descKey, "string", packageName)
            item.copy(
                label = if (labelResId != 0) ctx.getString(labelResId) else item.label,
                description = if (descResId != 0) ctx.getString(descResId) else item.description
            )
        }
    }

    companion object {
        private const val TAG = "GenerateVM"

        val FALLBACK_LANGUAGES = listOf(
            StyleItem("en", "English", "\uD83C\uDDFA\uD83C\uDDF8", "English"),
            StyleItem("es", "Spanish", "\uD83C\uDDEA\uD83C\uDDF8", "Español"),
            StyleItem("fr", "French", "\uD83C\uDDEB\uD83C\uDDF7", "Français"),
            StyleItem("ar", "Arabic", "\uD83C\uDDF8\uD83C\uDDE6", "العربية"),
            StyleItem("de", "German", "\uD83C\uDDE9\uD83C\uDDEA", "Deutsch"),
            StyleItem("zh", "Chinese", "\uD83C\uDDE8\uD83C\uDDF3", "中文"),
            StyleItem("pt", "Portuguese", "\uD83C\uDDE7\uD83C\uDDF7", "Português"),
            StyleItem("hi", "Hindi", "\uD83C\uDDEE\uD83C\uDDF3", "हिन्दी"),
            StyleItem("ja", "Japanese", "\uD83C\uDDEF\uD83C\uDDF5", "日本語"),
            StyleItem("ko", "Korean", "\uD83C\uDDF0\uD83C\uDDF7", "한국어"),
            StyleItem("it", "Italian", "\uD83C\uDDEE\uD83C\uDDF9", "Italiano"),
            StyleItem("nl", "Dutch", "\uD83C\uDDF3\uD83C\uDDF1", "Nederlands"),
            StyleItem("ru", "Russian", "\uD83C\uDDF7\uD83C\uDDFA", "Русский"),
            StyleItem("tr", "Turkish", "\uD83C\uDDF9\uD83C\uDDF7", "Türkçe"),
        )
    }
}
