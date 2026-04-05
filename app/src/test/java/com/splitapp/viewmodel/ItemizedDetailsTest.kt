package com.splitapp.viewmodel

import com.splitapp.model.OrderItem
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Tests for buildItemizedDetails — the breakdown text included
 * as notes on the Splitwise expense for itemized splits.
 */
class ItemizedDetailsTest {

    @Test
    fun `details includes item breakdown header`() {
        val details = buildDetails(
            items = listOf(OrderItem(id = "1", name = "Milk", price = 50.0)),
            buckets = mapOf("1" to setOf(1L, 2L)),
            allIds = setOf(1L, 2L),
            names = mapOf(1L to "You", 2L to "Alice")
        )
        assertTrue(details.startsWith("Item breakdown:"))
    }

    @Test
    fun `details lists each item with price and assignees`() {
        val details = buildDetails(
            items = listOf(
                OrderItem(id = "1", name = "Milk", price = 50.0),
                OrderItem(id = "2", name = "Bread", price = 30.0)
            ),
            buckets = mapOf("1" to setOf(1L, 2L), "2" to setOf(2L)),
            allIds = setOf(1L, 2L),
            names = mapOf(1L to "You", 2L to "Alice")
        )
        assertTrue(details.contains("• Milk — ₹50.00 → Everyone"))
        assertTrue(details.contains("• Bread — ₹30.00 → Alice"))
    }

    @Test
    fun `details shows Everyone when all participants assigned`() {
        val details = buildDetails(
            items = listOf(OrderItem(id = "1", name = "Tax", price = 10.0, isFee = true)),
            buckets = mapOf("1" to setOf(1L, 2L, 3L)),
            allIds = setOf(1L, 2L, 3L),
            names = mapOf(1L to "You", 2L to "Alice", 3L to "Bob")
        )
        assertTrue(details.contains("Everyone"))
    }

    @Test
    fun `details marks fee items`() {
        val details = buildDetails(
            items = listOf(OrderItem(id = "1", name = "Delivery fee", price = 25.0, isFee = true)),
            buckets = mapOf("1" to setOf(1L, 2L)),
            allIds = setOf(1L, 2L),
            names = mapOf(1L to "You", 2L to "Alice")
        )
        assertTrue(details.contains("[Fee]"))
    }

    @Test
    fun `details does not mark non-fee items`() {
        val details = buildDetails(
            items = listOf(OrderItem(id = "1", name = "Milk", price = 50.0, isFee = false)),
            buckets = mapOf("1" to setOf(1L)),
            allIds = setOf(1L, 2L),
            names = mapOf(1L to "You", 2L to "Alice")
        )
        assertFalse(details.contains("[Fee]"))
    }

    @Test
    fun `details includes per-person totals`() {
        val details = buildDetails(
            items = listOf(
                OrderItem(id = "1", name = "Milk", price = 50.0),
                OrderItem(id = "2", name = "Bread", price = 30.0)
            ),
            buckets = mapOf("1" to setOf(1L, 2L), "2" to setOf(1L)),
            allIds = setOf(1L, 2L),
            names = mapOf(1L to "You", 2L to "Alice")
        )
        assertTrue(details.contains("Per-person totals:"))
        // You: 50/2 + 30 = 55, Alice: 50/2 = 25
        assertTrue(details.contains("• You: ₹55.00"))
        assertTrue(details.contains("• Alice: ₹25.00"))
    }

    @Test
    fun `details lists individual names when subset assigned`() {
        val details = buildDetails(
            items = listOf(OrderItem(id = "1", name = "Chips", price = 40.0)),
            buckets = mapOf("1" to setOf(2L, 3L)),
            allIds = setOf(1L, 2L, 3L),
            names = mapOf(1L to "You", 2L to "Alice", 3L to "Bob")
        )
        // Should list Alice and Bob sorted, not "Everyone"
        assertTrue(details.contains("Alice, Bob"))
        assertFalse(details.contains("Everyone"))
    }

    // =========================================================================
    // Helper — mirrors SplitViewModel.buildItemizedDetails
    // =========================================================================

    private fun buildDetails(
        items: List<OrderItem>,
        buckets: Map<String, Set<Long>>,
        allIds: Set<Long>,
        names: Map<Long, String>
    ): String {
        val lines = mutableListOf<String>()
        lines.add("Item breakdown:")
        for (item in items) {
            val bucket = buckets[item.id] ?: allIds
            val nameList = bucket.map { names[it] ?: "Unknown" }.sorted()
            val assignedTo = if (bucket == allIds) "Everyone" else nameList.joinToString(", ")
            val feeTag = if (item.isFee) " [Fee]" else ""
            lines.add("• ${item.name}$feeTag — ₹${"%.2f".format(item.price)} → $assignedTo")
        }

        lines.add("")
        lines.add("Per-person totals:")
        val totals = allIds.associateWith { 0.0 }.toMutableMap()
        for (item in items) {
            val bucket = buckets[item.id] ?: allIds
            if (bucket.isEmpty()) continue
            val share = item.price / bucket.size
            for (pid in bucket) {
                totals[pid] = (totals[pid] ?: 0.0) + share
            }
        }
        for ((pid, amount) in totals.entries.sortedBy { names[it.key] ?: "" }) {
            val name = names[pid] ?: "Unknown"
            val rounded = BigDecimal(amount).setScale(2, RoundingMode.HALF_UP)
            lines.add("• $name: ₹$rounded")
        }

        return lines.joinToString("\n")
    }
}
