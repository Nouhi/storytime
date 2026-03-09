package com.storytime.app.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.storytime.app.StorytimeApp
import com.storytime.app.model.FamilyMemberCreateRequest
import com.storytime.app.model.FamilyMemberResponse
import com.storytime.app.model.FamilyMemberUpdateRequest
import com.storytime.app.network.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class FamilyMembersViewModel(application: Application) : AndroidViewModel(application) {

    private val api = ApiClient.service

    private val _members = MutableStateFlow<List<FamilyMemberResponse>>(emptyList())
    val members: StateFlow<List<FamilyMemberResponse>> = _members.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() { _errorMessage.value = null }

    fun loadMembers() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _members.value = api.getFamilyMembers()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    fun addMember(name: String, role: String, description: String? = null) {
        viewModelScope.launch {
            try {
                api.createFamilyMember(FamilyMemberCreateRequest(
                    name.trim(),
                    role.trim(),
                    description?.trim()?.ifEmpty { null }
                ))
                loadMembers()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add member: ${e.message}"
            }
        }
    }

    fun updateMember(id: Int, name: String, role: String, description: String? = null) {
        viewModelScope.launch {
            try {
                api.updateFamilyMember(id, FamilyMemberUpdateRequest(
                    name.trim(),
                    role.trim(),
                    description?.trim()?.ifEmpty { null }
                ))
                loadMembers()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update: ${e.message}"
            }
        }
    }

    fun deleteMember(id: Int) {
        viewModelScope.launch {
            try {
                api.deleteFamilyMember(id)
                _members.value = _members.value.filter { it.id != id }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete: ${e.message}"
            }
        }
    }

    fun uploadMemberPhoto(memberId: Int, uri: Uri) {
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
                val typeBody = "family-member".toRequestBody("text/plain".toMediaType())
                val memberIdBody = memberId.toString().toRequestBody("text/plain".toMediaType())

                api.uploadPhoto(file = part, type = typeBody, memberId = memberIdBody)
                loadMembers()
            } catch (e: Exception) {
                _errorMessage.value = "Photo upload failed: ${e.message}"
            }
        }
    }

    companion object {
        val ROLES = listOf(
            "mom" to "👩",
            "dad" to "👨",
            "brother" to "👦",
            "sister" to "👧",
            "grandma" to "👵",
            "grandpa" to "👴",
            "aunt" to "👩",
            "uncle" to "👨",
            "pet" to "🐾",
            "friend" to "👫",
            "companion" to "🤝",
            "classmate" to "🎒",
            "neighbor" to "🏠",
            "magical-friend" to "✨",
            "other" to "👤"
        )

        fun roleEmoji(role: String): String {
            return ROLES.firstOrNull { it.first == role }?.second ?: "👤"
        }
    }
}
