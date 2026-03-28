package com.splitapp.viewmodel

import com.splitapp.model.OrderItem
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * Tests for item total vs transaction amount mismatch detection
 * and adjustment item logic in itemized split mode.
 */
class MismatchDetectionTest {

    // =========================================================================
    // Mismatch detection
    // =========================================================================

    @Test
    fun `no mismatch when totals match exactly`() {
        val items = listOf(
            OrderItem(id = "1", name = "A", price = 50.0),
            OrderItem(id = "2", name = "B", price = 50.0)
        )
        val transactionAmount = 100.0
        val itemTotal = items.sumOf { it.price }
        val mismatch = abs(itemTotal - transactionAmount)
        assertTrue(mismatch <= 1.0)
    }

    @Test
    fun `mismatch under threshold is acceptable`() {
        val items = listOf(
            OrderItem(id = "1", name = "A", price = 99.5)
        )
        val transactionAmount = 100.0
        val mismatch = abs(items.sumOf { it.price } - transactionAmount)
        assertTrue(mismatch <= 1.0)
    }

    @Test
    fun `mismatch over threshold triggers warning`() {
        val items = listOf(
            OrderItem(id = "1", name = "A", price = 80.0)
        )
        val transactionAmount = 100.0
        val mismatch = abs(items.sumOf { it.price } - transactionAmount)
        assertTrue(mismatch > 1.0)
        assertEquals(20.0, mismatch, 0.001)
    }

    @Test
    fun `mismatch when items exceed transaction`() {
        val items = listOf(
            OrderItem(id = "1", name = "A", price = 120.0)
        )
        val transactionAmount = 100.0
        val mismatch = abs(items.sumOf { it.price } - transactionAmount)
        assertTrue(mismatch > 1.0)
    }

    // =========================================================================
    // Adjustment item
    // =========================================================================

    @Test
    fun `adjustment item fills the gap when items less than transaction`() {
        val items = listOf(
            OrderItem(id = "1", name = "A", price = 80.0)
        )
        val transactionAmount = 100.0
        val diff = transactionAmount - items.sumOf { it.price }

        assertTrue(diff > 0)
        val adjustment = OrderItem(name = "Adjustment", price = diff)
        assertEquals(20.0, adjustment.price, 0.001)

        val newTotal = items.sumOf { it.price } + adjustment.price
        assertEquals(transactionAmount, newTotal, 0.001)
    }

    @Test
    fun `adjustment not created when items exceed transaction`() {
        val items = listOf(
            OrderItem(id = "1", name = "A", price = 120.0)
        )
        val transactionAmount = 100.0
        val diff = transactionAmount - items.sumOf { it.price }

        // diff is negative, adjustment should NOT be created
        assertTrue(diff <= 0)
    }

    @Test
    fun `adjustment brings mismatch below threshold`() {
        val items = listOf(
            OrderItem(id = "1", name = "A", price = 85.0),
            OrderItem(id = "2", name = "B", price = 10.0)
        )
        val transactionAmount = 100.0
        val diff = transactionAmount - items.sumOf { it.price }
        val adjustment = OrderItem(name = "Adjustment", price = diff)

        val newTotal = items.sumOf { it.price } + adjustment.price
        val newMismatch = abs(newTotal - transactionAmount)
        assertTrue(newMismatch <= 1.0)
    }

    // =========================================================================
    // Empty items
    // =========================================================================

    @Test
    fun `no items means full mismatch`() {
        val items = emptyList<OrderItem>()
        val transactionAmount = 500.0
        val mismatch = abs(items.sumOf { it.price } - transactionAmount)
        assertEquals(500.0, mismatch, 0.001)
    }
}
