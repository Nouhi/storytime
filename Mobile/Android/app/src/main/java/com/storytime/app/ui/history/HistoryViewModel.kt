package com.storytime.app.ui.history

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.storytime.app.R
import com.storytime.app.model.StoryHistoryEntry
import com.storytime.app.model.StoryPage
import com.storytime.app.network.ApiClient
import com.storytime.app.util.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val api = ApiClient.service

    // List state
    private val _stories = MutableStateFlow<List<StoryHistoryEntry>>(emptyList())
    val stories: StateFlow<List<StoryHistoryEntry>> = _stories.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Detail state
    private val _storyPages = MutableStateFlow<List<StoryPage>>(emptyList())
    val storyPages: StateFlow<List<StoryPage>> = _storyPages.asStateFlow()

    private val _storyTitle = MutableStateFlow("")
    val storyTitle: StateFlow<String> = _storyTitle.asStateFlow()

    private val _isLoadingDetail = MutableStateFlow(false)
    val isLoadingDetail: StateFlow<Boolean> = _isLoadingDetail.asStateFlow()

    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    fun updateCurrentPage(page: Int) { _currentPage.value = page }

    fun loadHistory() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                _stories.value = api.getStoryHistory()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
            _isLoading.value = false
        }
    }

    fun loadStoryDetail(id: Int) {
        viewModelScope.launch {
            _isLoadingDetail.value = true
            _currentPage.value = 1
            try {
                val data = api.getStoryData(id)
                _storyPages.value = data.pages
                _storyTitle.value = data.title
            } catch (e: Exception) {
                _storyPages.value = emptyList()
                _storyTitle.value = getApplication<Application>().getString(R.string.error_title)
            }
            _isLoadingDetail.value = false
        }
    }

    fun pageImageUrl(storyId: Int, page: Int): String {
        return ApiClient.imageUrl("/api/story-history/$storyId/pages?page=$page")
    }

    fun deleteStory(id: Int) {
        viewModelScope.launch {
            try {
                api.deleteStory(id)
                _stories.value = _stories.value.filter { it.id != id }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    // Booklet download state
    sealed class BookletState {
        data object Idle : BookletState()
        data object Downloading : BookletState()
        data class Done(val message: String, val isError: Boolean = false) : BookletState()
    }

    private val _bookletState = MutableStateFlow<BookletState>(BookletState.Idle)
    val bookletState: StateFlow<BookletState> = _bookletState.asStateFlow()
    fun dismissBookletOverlay() { _bookletState.value = BookletState.Idle }

    // Toast message for user feedback
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()
    fun clearToast() { _toastMessage.value = null }

    fun downloadEpub(storyId: Int, title: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "downloadEpub: storyId=$storyId")
                    val response = api.downloadHistoryEpub(storyId)
                    Log.d(TAG, "downloadEpub: response code=${response.code()}")
                    if (!response.isSuccessful) {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "downloadEpub: HTTP error body=$errorBody")
                        return@withContext "EPUB download failed (HTTP ${response.code()})"
                    }
                    val body = response.body() ?: return@withContext "EPUB download returned empty response"
                    val bytes = body.bytes()
                    Log.d(TAG, "downloadEpub: received ${bytes.size} bytes")
                    val filename = "${FileHelper.sanitizeFilename(title)}.epub"
                    FileHelper.saveToDownloads(getApplication(), bytes, filename, "application/epub+zip")
                } catch (e: Exception) {
                    Log.e(TAG, "downloadEpub: exception", e)
                    "Failed to download EPUB: ${e.message ?: e.javaClass.simpleName}"
                }
            }
            _toastMessage.value = result
        }
    }

    fun downloadPdf(storyId: Int, title: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "downloadPdf: storyId=$storyId")
                    val response = api.downloadHistoryPdf(storyId)
                    Log.d(TAG, "downloadPdf: response code=${response.code()}")
                    if (!response.isSuccessful) {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "downloadPdf: HTTP error body=$errorBody")
                        return@withContext "PDF download failed (HTTP ${response.code()})"
                    }
                    val body = response.body() ?: return@withContext "PDF download returned empty response"
                    val bytes = body.bytes()
                    Log.d(TAG, "downloadPdf: received ${bytes.size} bytes")
                    val filename = "${FileHelper.sanitizeFilename(title)}.pdf"
                    FileHelper.saveToDownloads(getApplication(), bytes, filename, "application/pdf")
                } catch (e: Exception) {
                    Log.e(TAG, "downloadPdf: exception", e)
                    "Failed to download PDF: ${e.message ?: e.javaClass.simpleName}"
                }
            }
            _toastMessage.value = result
        }
    }

    fun downloadBookletPdf(storyId: Int, title: String) {
        viewModelScope.launch {
            _bookletState.value = BookletState.Downloading
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "downloadBookletPdf: storyId=$storyId")
                    val response = api.downloadHistoryBookletPdf(storyId)
                    Log.d(TAG, "downloadBookletPdf: response code=${response.code()}")
                    if (!response.isSuccessful) {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "downloadBookletPdf: HTTP error body=$errorBody")
                        _bookletState.value = BookletState.Done("Download failed (HTTP ${response.code()})", isError = true)
                        return@withContext
                    }
                    val body = response.body()
                    if (body == null) {
                        _bookletState.value = BookletState.Done("Download returned empty response", isError = true)
                        return@withContext
                    }
                    val bytes = body.bytes()
                    Log.d(TAG, "downloadBookletPdf: received ${bytes.size} bytes")
                    val filename = "${FileHelper.sanitizeFilename(title)}-booklet.pdf"
                    FileHelper.saveToDownloads(getApplication(), bytes, filename, "application/pdf")
                    _bookletState.value = BookletState.Done("Booklet downloaded and ready to print!")
                } catch (e: Exception) {
                    Log.e(TAG, "downloadBookletPdf: exception", e)
                    _bookletState.value = BookletState.Done("Failed: ${e.message ?: e.javaClass.simpleName}", isError = true)
                }
            }
        }
    }

    val totalCost: Double
        get() = _stories.value.sumOf { it.totalCost }

    companion object {
        private const val TAG = "HistoryVM"

        fun formatCost(cost: Double): String {
            return if (cost > 0 && cost < 0.01) "<$0.01"
            else String.format("$%.2f", cost)
        }
    }
}
