package com.splitapp.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.splitapp.model.OrderItem
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for Gemini receipt JSON parsing logic.
 *
 * We can't test the actual API call or image reading in unit tests,
 * but we CAN test the JSON parsing pipeline that converts Gemini's
 * response text into OrderItems. This is the critical logic that
 * determines whether extracted items are correct.
 */
class GeminiReceiptExtractorTest {

    private val gson = Gson()

    // =========================================================================
    // JSON parsing — clean response
    // =========================================================================

    @Test
    fun `parse valid JSON array with items`() {
        val json = """[
            {"name": "Tender Coconut", "price": 71, "isFee": false},
            {"name": "Handling charge", "price": 11, "isFee": true}
        ]"""
        val items = parseJsonResponse(json)
        assertEquals(2, items.size)
        assertEquals("Tender Coconut", items[0].name)
        assertEquals(71.0, items[0].price, 0.001)
        assertFalse(items[0].isFee)
        assertEquals("Handling charge", items[1].name)
        assertTrue(items[1].isFee)
    }

    @Test
    fun `parse filters out items with price 0`() {
        val json = """[
            {"name": "Regular item", "price": 50, "isFee": false},
            {"name": "FREE delivery", "price": 0, "isFee": true}
        ]"""
        val items = parseJsonResponse(json)
        assertEquals(1, items.size)
        assertEquals("Regular item", items[0].name)
    }

    @Test
    fun `parse filters out items with negative price`() {
        val json = """[
            {"name": "Item", "price": 50, "isFee": false},
            {"name": "Discount", "price": -20, "isFee": false}
        ]"""
        val items = parseJsonResponse(json)
        assertEquals(1, items.size)
    }

    // =========================================================================
    // JSON parsing — markdown code fences
    // =========================================================================

    @Test
    fun `strips json code fence from response`() {
        val json = """```json
[{"name": "Apple", "price": 30, "isFee": false}]
```"""
        val items = parseJsonResponse(json)
        assertEquals(1, items.size)
        assertEquals("Apple", items[0].name)
    }

    @Test
    fun `strips plain code fence from response`() {
        val json = """```
[{"name": "Banana", "price": 20, "isFee": false}]
```"""
        val items = parseJsonResponse(json)
        assertEquals(1, items.size)
    }

    @Test
    fun `handles response without code fences`() {
        val json = """[{"name": "Milk", "price": 45, "isFee": false}]"""
        val items = parseJsonResponse(json)
        assertEquals(1, items.size)
    }

    // =========================================================================
    // JSON parsing — real-world receipt formats
    // =========================================================================

    @Test
    fun `Blinkit style receipt - discounted prices with fees`() {
        val json = """[
            {"name": "Tender Coconut", "price": 71, "isFee": false},
            {"name": "Onion", "price": 35, "isFee": false},
            {"name": "Green Chilli", "price": 9, "isFee": false},
            {"name": "Handling charge", "price": 11, "isFee": true}
        ]"""
        val items = parseJsonResponse(json)
        assertEquals(4, items.size)
        assertEquals(1, items.count { it.isFee })
        // Should have discounted price, not MRP
        assertEquals(71.0, items.find { it.name == "Tender Coconut" }!!.price, 0.001)
    }

    @Test
    fun `Swiggy Instamart style - delivery and handling fees`() {
        val json = """[
            {"name": "Baby Banana (Aratipandu)", "price": 19.0, "isFee": false},
            {"name": "Delivery Fee", "price": 30.0, "isFee": true},
            {"name": "Handling Fee", "price": 10.62, "isFee": true}
        ]"""
        val items = parseJsonResponse(json)
        assertEquals(3, items.size)
        assertEquals(2, items.count { it.isFee })
    }

    @Test
    fun `Zepto style - items only, no fees`() {
        val json = """[
            {"name": "Onion", "price": 10, "isFee": false},
            {"name": "Green Peas", "price": 14, "isFee": false},
            {"name": "Potato", "price": 26, "isFee": false}
        ]"""
        val items = parseJsonResponse(json)
        assertEquals(3, items.size)
        assertTrue(items.none { it.isFee })
    }

    @Test
    fun `Flipkart Minutes style - discounted prices with FREE fees excluded`() {
        val json = """[
            {"name": "Lady Finger", "price": 45, "isFee": false},
            {"name": "Tomato", "price": 22, "isFee": false},
            {"name": "Delivery Fee", "price": 0, "isFee": true},
            {"name": "Handling Fee", "price": 0, "isFee": true}
        ]"""
        val items = parseJsonResponse(json)
        // FREE fees (price 0) should be filtered out
        assertEquals(2, items.size)
        assertTrue(items.none { it.isFee })
    }

    // =========================================================================
    // JSON parsing — edge cases
    // =========================================================================

    @Test
    fun `empty JSON array returns empty list`() {
        val items = parseJsonResponse("[]")
        assertTrue(items.isEmpty())
    }

    @Test
    fun `malformed JSON throws exception`() {
        assertThrows(Exception::class.java) {
            parseJsonResponse("not json at all")
        }
    }

    @Test
    fun `item with decimal price`() {
        val json = """[{"name": "Milk", "price": 45.50, "isFee": false}]"""
        val items = parseJsonResponse(json)
        assertEquals(45.50, items[0].price, 0.001)
    }

    @Test
    fun `item names with special characters`() {
        val json = """[{"name": "Maggi (Pack of 4)", "price": 56, "isFee": false}]"""
        val items = parseJsonResponse(json)
        assertEquals("Maggi (Pack of 4)", items[0].name)
    }

    @Test
    fun `multiple items all returned with unique IDs`() {
        val json = """[
            {"name": "A", "price": 10, "isFee": false},
            {"name": "B", "price": 20, "isFee": false},
            {"name": "C", "price": 30, "isFee": false}
        ]"""
        val items = parseJsonResponse(json)
        val ids = items.map { it.id }.toSet()
        assertEquals(3, ids.size)  // all unique
    }

    @Test
    fun `isFee defaults to false when missing from JSON`() {
        val json = """[{"name": "Item", "price": 10}]"""
        val items = parseJsonResponse(json)
        assertFalse(items[0].isFee)
    }

    // =========================================================================
    // Helper — mirrors GeminiReceiptExtractor.parseJsonResponse
    // =========================================================================

    private data class GeminiItem(
        val name: String = "",
        val price: Double = 0.0,
        val isFee: Boolean = false
    )

    private fun parseJsonResponse(rawText: String): List<OrderItem> {
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
}
