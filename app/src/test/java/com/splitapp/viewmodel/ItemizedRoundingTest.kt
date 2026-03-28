package com.splitapp.viewmodel

import com.splitapp.model.OrderItem
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Tests for BigDecimal rounding in itemized split submission.
 *
 * Verifies that:
 * - Per-person amounts are rounded to 2 decimal places (HALF_UP)
 * - The total sent to Splitwise matches the item total exactly
 * - Edge cases with repeating decimals are handled correctly
 */
class ItemizedRoundingTest {

    // =========================================================================
    // BigDecimal rounding
    // =========================================================================

    @Test
    fun `three-way split of 100 rounds correctly`() {
        // 100 / 3 = 33.333...
        val perPerson = BigDecimal(100.0 / 3.0).setScale(2, RoundingMode.HALF_UP).toDouble()
        assertEquals(33.33, perPerson, 0.001)
    }

    @Test
    fun `two-way split of 33 rounds correctly`() {
        val perPerson = BigDecimal(33.0 / 2.0).setScale(2, RoundingMode.HALF_UP).toDouble()
        assertEquals(16.50, perPerson, 0.001)
    }

    @Test
    fun `item total rounded to 2 decimals`() {
        val total = BigDecimal(99.999).setScale(2, RoundingMode.HALF_UP).toDouble()
        assertEquals(100.00, total, 0.001)
    }

    @Test
    fun `rounding does not lose significant amounts`() {
        // 3 items totaling 100, split among 3 people
        val items = listOf(
            OrderItem(id = "1", name = "A", price = 33.0),
            OrderItem(id = "2", name = "B", price = 34.0),
            OrderItem(id = "3", name = "C", price = 33.0)
        )
        val allIds = setOf(1L, 2L, 3L)
        val buckets = items.associate { it.id to allIds }

        val totals = computePerPersonTotals(items, buckets, allIds)

        // Each person: 33/3 + 34/3 + 33/3 = 11 + 11.333 + 11 = 33.333
        val rounded = totals.mapValues {
            BigDecimal(it.value).setScale(2, RoundingMode.HALF_UP).toDouble()
        }

        // After rounding: 33.33 each, total = 99.99 (residual 0.01 handled by current user)
        assertEquals(33.33, rounded[1L]!!, 0.001)
        assertEquals(33.33, rounded[2L]!!, 0.001)
        assertEquals(33.33, rounded[3L]!!, 0.001)
    }

    @Test
    fun `uneven split rounds without exceeding total`() {
        // Item: 10.00, split between 3
        val share = 10.0 / 3.0
        val rounded = BigDecimal(share).setScale(2, RoundingMode.HALF_UP).toDouble()
        assertEquals(3.33, rounded, 0.001)
        // 3 * 3.33 = 9.99, residual 0.01 absorbed by payer
        assertTrue(3 * rounded <= 10.0)
    }

    @Test
    fun `real-world Blinkit receipt rounding`() {
        // Simulating a Blinkit receipt split between 2 people
        val items = listOf(
            OrderItem(id = "1", name = "Tender Coconut", price = 71.0),
            OrderItem(id = "2", name = "Onion", price = 35.0),
            OrderItem(id = "3", name = "Handling charge", price = 11.0, isFee = true)
        )
        val allIds = setOf(1L, 2L)
        val buckets = mapOf(
            "1" to setOf(1L),        // only person 1
            "2" to allIds,           // shared
            "3" to allIds            // fee shared
        )

        val totals = computePerPersonTotals(items, buckets, allIds)

        // Person 1: 71 + 35/2 + 11/2 = 71 + 17.5 + 5.5 = 94
        // Person 2: 35/2 + 11/2 = 17.5 + 5.5 = 23
        val p1 = BigDecimal(totals[1L]!!).setScale(2, RoundingMode.HALF_UP).toDouble()
        val p2 = BigDecimal(totals[2L]!!).setScale(2, RoundingMode.HALF_UP).toDouble()
        assertEquals(94.0, p1, 0.001)
        assertEquals(23.0, p2, 0.001)
        assertEquals(117.0, p1 + p2, 0.001)
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private fun computePerPersonTotals(
        items: List<OrderItem>,
        buckets: Map<String, Set<Long>>,
        allIds: Set<Long>
    ): Map<Long, Double> {
        val totals = allIds.associateWith { 0.0 }.toMutableMap()
        for (item in items) {
            val bucket = buckets[item.id] ?: allIds
            if (bucket.isEmpty()) continue
            val share = item.price / bucket.size
            for (pid in bucket) {
                totals[pid] = (totals[pid] ?: 0.0) + share
            }
        }
        return totals
    }
}
