package com.splitapp.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

// --- Request models ---

data class GeminiRequest(
    val contents: List<GeminiContent>
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String? = null,
    @SerializedName("inline_data") val inlineData: GeminiInlineData? = null
)

data class GeminiInlineData(
    @SerializedName("mime_type") val mimeType: String,
    val data: String  // base64-encoded image
)

// --- Response models ---

data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    val content: GeminiContent?
)

// --- Retrofit interface ---

interface GeminiApi {

    @POST("v1beta/models/gemini-3.1-flash-lite-preview:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}
