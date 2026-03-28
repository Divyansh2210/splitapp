package com.splitapp.api

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for Gemini API request/response JSON serialization.
 * Ensures our models serialize correctly for the Gemini REST API.
 */
class GeminiApiModelsTest {

    private val gson = Gson()

    @Test
    fun `GeminiRequest serializes with text and inline_data parts`() {
        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(
                            inlineData = GeminiInlineData(
                                mimeType = "image/jpeg",
                                data = "base64data"
                            )
                        ),
                        GeminiPart(text = "Extract items")
                    )
                )
            )
        )
        val json = gson.toJson(request)
        assertTrue(json.contains("\"mime_type\":\"image/jpeg\""))
        assertTrue(json.contains("\"data\":\"base64data\""))
        assertTrue(json.contains("\"text\":\"Extract items\""))
    }

    @Test
    fun `GeminiResponse deserialization with candidates`() {
        val json = """
        {
            "candidates": [{
                "content": {
                    "parts": [
                        {"text": "[{\"name\":\"Apple\",\"price\":50}]"}
                    ]
                }
            }]
        }"""
        val response = gson.fromJson(json, GeminiResponse::class.java)
        assertNotNull(response.candidates)
        assertEquals(1, response.candidates!!.size)
        val text = response.candidates!![0].content!!.parts[0].text
        assertTrue(text!!.contains("Apple"))
    }

    @Test
    fun `GeminiResponse with null candidates`() {
        val json = """{"candidates": null}"""
        val response = gson.fromJson(json, GeminiResponse::class.java)
        assertNull(response.candidates)
    }

    @Test
    fun `GeminiResponse with empty candidates`() {
        val json = """{"candidates": []}"""
        val response = gson.fromJson(json, GeminiResponse::class.java)
        assertTrue(response.candidates!!.isEmpty())
    }

    @Test
    fun `GeminiInlineData uses snake_case in JSON`() {
        val data = GeminiInlineData(mimeType = "image/png", data = "abc123")
        val json = gson.toJson(data)
        assertTrue(json.contains("\"mime_type\""))
        assertFalse(json.contains("\"mimeType\""))
    }

    @Test
    fun `GeminiPart with inline_data uses snake_case in JSON`() {
        val part = GeminiPart(
            inlineData = GeminiInlineData("image/jpeg", "data")
        )
        val json = gson.toJson(part)
        assertTrue(json.contains("\"inline_data\""))
        assertFalse(json.contains("\"inlineData\""))
    }
}
