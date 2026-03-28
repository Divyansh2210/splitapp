package com.splitapp.api

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.splitapp.data.PreferencesStore
import com.splitapp.model.OrderItem
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class GeminiReceiptExtractor(private val context: Context) {

    private val prefs = PreferencesStore(context)
    private val gson = Gson()

    private val api: GeminiApi by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiApi::class.java)
    }

    suspend fun extractItems(imageUri: Uri): List<OrderItem> {
        val apiKey = prefs.geminiApiKey.first()
        if (apiKey.isBlank()) {
            throw IllegalStateException("Gemini API key not set. Add it in Settings.")
        }

        val (base64Image, mimeType) = readImageAsBase64(imageUri)

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(
                            inlineData = GeminiInlineData(
                                mimeType = mimeType,
                                data = base64Image
                            )
                        ),
                        GeminiPart(text = EXTRACTION_PROMPT)
                    )
                )
            )
        )

        val response = api.generateContent(apiKey, request)

        val rawText = response.candidates
            ?.firstOrNull()?.content?.parts
            ?.firstOrNull { it.text != null }?.text
            ?: throw Exception("Empty response from Gemini")

        return parseJsonResponse(rawText)
    }

    private fun readImageAsBase64(uri: Uri): Pair<String, String> {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Cannot read image")
        val bytes = inputStream.use { it.readBytes() }
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        return Pair(base64, mimeType)
    }

    private fun parseJsonResponse(rawText: String): List<OrderItem> {
        // Strip markdown code fences if present (```json ... ```)
        val jsonText = rawText
            .replace(Regex("""^```\w*\n?"""), "")
            .replace(Regex("""\n?```$"""), "")
            .trim()

        val type = object : TypeToken<List<GeminiItem>>() {}.type
        val items: List<GeminiItem> = try {
            gson.fromJson(jsonText, type)
        } catch (e: Exception) {
            throw Exception("Failed to parse receipt data: ${e.message}")
        }

        return items
            .filter { it.price > 0 }
            .map { OrderItem(name = it.name, price = it.price, isFee = it.isFee) }
    }

    /** Intermediate model matching the JSON schema we ask Gemini to produce. */
    private data class GeminiItem(
        val name: String = "",
        val price: Double = 0.0,
        val isFee: Boolean = false
    )

    companion object {
        private val EXTRACTION_PROMPT = """
Extract all purchased items from this grocery delivery receipt screenshot.
Return ONLY a JSON array, no other text. Each object must have:
- "name": item name (without quantity prefixes like "1 x")
- "price": actual price paid as a number (use discounted price, not MRP/crossed-out price)
- "isFee": true if this is a delivery fee, handling charge, platform fee, service charge, packaging fee, or similar surcharge; false for regular items

Rules:
- Skip totals, subtotals, grand totals, bill totals
- Skip discounts, coupons, cashback, savings, MRP-only lines
- Skip items with price 0 or marked FREE
- For fees marked FREE or ₹0, skip them entirely
- Use the actual/discounted price, never the original/MRP/crossed-out price

Example output: [{"name": "Tender Coconut", "price": 71, "isFee": false}, {"name": "Handling charge", "price": 11, "isFee": true}]
        """.trimIndent()
    }
}
