package com.storytime.app.model

import kotlinx.serialization.Serializable

// MARK: - Settings

@Serializable
data class SettingsResponse(
    val id: Int,
    val kidName: String,
    val kidGender: String,
    val readingLevel: String,
    val kidPhotoPath: String,
    val anthropicApiKey: String,
    val googleAiApiKey: String,
    val updatedAt: String
)

@Serializable
data class SettingsUpdateRequest(
    val kidName: String? = null,
    val kidGender: String? = null,
    val readingLevel: String? = null,
    val anthropicApiKey: String? = null,
    val googleAiApiKey: String? = null
)

// MARK: - Styles

@Serializable
data class StylesResponse(
    val writingStyles: List<StyleItem>,
    val imageStyles: List<StyleItem>,
    val lessons: List<StyleItem>? = null,
    val defaults: StyleDefaults
)

@Serializable
data class StyleItem(
    val id: String,
    val label: String,
    val emoji: String,
    val description: String
)

@Serializable
data class StyleDefaults(
    val writingStyle: String,
    val imageStyle: String,
    val lesson: String? = null
)

// MARK: - Story Generation

@Serializable
data class GenerateRequest(
    val prompt: String,
    val writingStyle: String,
    val imageStyle: String,
    val lesson: String? = null,
    val characterIds: List<Int>? = null,
    val customWritingStyle: String? = null,
    val customImageStyle: String? = null,
    val customLesson: String? = null,
    val bedtimeStory: Boolean? = null
)

@Serializable
data class GenerateResponse(
    val storyId: String
)

@Serializable
data class StoryPage(
    val page: Int,
    val text: String,
    val imageDescription: String = ""
)

@Serializable
data class GenerationEvent(
    val type: String, // "progress", "complete", "error"
    val step: String? = null,
    val detail: String? = null,
    val progress: Double? = null,
    val epubUrl: String? = null,
    val storyId: String? = null,
    val storyPages: List<StoryPage>? = null,
    val hasImages: Boolean? = null,
    val message: String? = null
)

// MARK: - Story History

@Serializable
data class StoryHistoryEntry(
    val id: Int,
    val title: String,
    val prompt: String,
    val createdAt: String,
    val claudeInputTokens: Int,
    val claudeOutputTokens: Int,
    val geminiImageCount: Int,
    val claudeCost: Double,
    val geminiCost: Double,
    val totalCost: Double,
    val pdfPath: String
)

@Serializable
data class StoryDataResponse(
    val pages: List<StoryPage>,
    val title: String
)

// MARK: - Family Members

@Serializable
data class FamilyMemberResponse(
    val id: Int,
    val name: String,
    val role: String,
    val photoPath: String? = null,
    val description: String? = null
)

@Serializable
data class FamilyMemberCreateRequest(
    val name: String,
    val role: String,
    val description: String? = null
)

@Serializable
data class FamilyMemberUpdateRequest(
    val name: String? = null,
    val role: String? = null,
    val description: String? = null
)

// MARK: - Upload

@Serializable
data class UploadResponse(
    val photoPath: String
)

// MARK: - Health

@Serializable
data class HealthResponse(
    val status: String,
    val timestamp: String
)

// MARK: - Delete

@Serializable
data class DeleteResponse(
    val success: Boolean
)
