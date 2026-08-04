package com.haneul.medassist.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface ApiService {
    @GET("api/v1/home")
    suspend fun home(@Header("X-Demo-User-Id") userId: String = DEMO_USER): HomeResponse

    @GET("api/v1/medications?active=true")
    suspend fun medications(): List<Medication>

    @POST("api/v1/medications/{id}/dose-logs")
    suspend fun doseLog(@Path("id") id: String, @Body request: DoseLogRequest): Medication

    @Multipart
    @POST("api/v1/prescription-drafts")
    suspend fun createDraft(
        @Part frontImage: MultipartBody.Part,
        @Part backImage: MultipartBody.Part,
        @Part("clientOcrText") clientOcrText: RequestBody,
    ): PrescriptionDraft

    @PATCH("api/v1/prescription-drafts/{id}")
    suspend fun updateDraft(@Path("id") id: String, @Body request: DraftUpdate): PrescriptionDraft

    @POST("api/v1/prescription-drafts/{id}/confirm")
    suspend fun confirmDraft(@Path("id") id: String): Medication

    @POST("api/v1/interaction-checks")
    suspend fun createCheck(
        @Header("Idempotency-Key") key: String,
        @Body request: InteractionRequest,
    ): Accepted

    @GET("api/v1/interaction-checks/{id}")
    suspend fun check(@Path("id") id: String): InteractionCheck

    @POST("api/v1/interaction-checks/{id}/save")
    suspend fun saveCheck(@Path("id") id: String): InteractionCheck

    @GET("api/v1/consultations")
    suspend fun consultations(): List<Consultation>

    @Multipart
    @POST("api/v1/consultations")
    suspend fun uploadConsultation(
        @Part audio: MultipartBody.Part,
        @Part("title") title: RequestBody,
        @Part("hospitalName") hospitalName: RequestBody,
        @Part("consultedAt") consultedAt: RequestBody,
        @Part("durationMs") durationMs: RequestBody,
        @Header("Idempotency-Key") key: String,
    ): Accepted

    @POST("api/v1/chat/sessions")
    suspend fun createChat(): ChatSession

    @POST("api/v1/chat/sessions/{id}/messages")
    suspend fun sendMessage(@Path("id") id: String, @Body request: ChatMessageRequest): ChatMessageAccepted

    companion object { const val DEMO_USER = "00000000-0000-0000-0000-000000000001" }
}

