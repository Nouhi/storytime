package com.storytime.app.network

import com.storytime.app.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // Health
    @GET("api/health")
    suspend fun checkHealth(): HealthResponse

    // Settings
    @GET("api/settings")
    suspend fun getSettings(): SettingsResponse

    @PUT("api/settings")
    suspend fun updateSettings(@Body body: SettingsUpdateRequest): SettingsResponse

    // Styles
    @GET("api/styles")
    suspend fun getStyles(): StylesResponse

    // Upload
    @Multipart
    @POST("api/upload")
    suspend fun uploadPhoto(
        @Part file: MultipartBody.Part,
        @Part("type") type: RequestBody,
        @Part("memberId") memberId: RequestBody? = null
    ): UploadResponse

    // Generation
    @POST("api/generate")
    suspend fun startGeneration(@Body body: GenerateRequest): GenerateResponse

    // Generation downloads
    @GET("api/generate/{storyId}/epub")
    @Streaming
    suspend fun downloadGeneratedEpub(@Path("storyId") storyId: String): Response<ResponseBody>

    @GET("api/generate/{storyId}/pdf")
    @Streaming
    suspend fun downloadGeneratedPdf(@Path("storyId") storyId: String): Response<ResponseBody>

    // Story History
    @GET("api/story-history")
    suspend fun getStoryHistory(): List<StoryHistoryEntry>

    @GET("api/story-history/{id}/story-data")
    suspend fun getStoryData(@Path("id") id: Int): StoryDataResponse

    @GET("api/story-history/{id}/download")
    @Streaming
    suspend fun downloadHistoryEpub(@Path("id") id: Int): Response<ResponseBody>

    @GET("api/story-history/{id}/pdf")
    @Streaming
    suspend fun downloadHistoryPdf(@Path("id") id: Int): Response<ResponseBody>

    @DELETE("api/story-history/{id}")
    suspend fun deleteStory(@Path("id") id: Int): DeleteResponse

    // Family Members
    @GET("api/family-members")
    suspend fun getFamilyMembers(): List<FamilyMemberResponse>

    @POST("api/family-members")
    suspend fun createFamilyMember(@Body body: FamilyMemberCreateRequest): FamilyMemberResponse

    @PATCH("api/family-members/{id}")
    suspend fun updateFamilyMember(
        @Path("id") id: Int,
        @Body body: FamilyMemberUpdateRequest
    ): FamilyMemberResponse

    @DELETE("api/family-members/{id}")
    suspend fun deleteFamilyMember(@Path("id") id: Int): DeleteResponse
}
