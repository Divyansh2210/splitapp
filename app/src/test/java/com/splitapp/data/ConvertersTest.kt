package com.splitapp.data

import com.splitapp.model.TransactionStatus
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for Room type converters.
 */
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `fromStatus converts PENDING to string`() {
        assertEquals("PENDING", converters.fromStatus(TransactionStatus.PENDING))
    }

    @Test
    fun `fromStatus converts DONE to string`() {
        assertEquals("DONE", converters.fromStatus(TransactionStatus.DONE))
    }

    @Test
    fun `fromStatus converts SKIPPED to string`() {
        assertEquals("SKIPPED", converters.fromStatus(TransactionStatus.SKIPPED))
    }

    @Test
    fun `toStatus converts PENDING string back`() {
        assertEquals(TransactionStatus.PENDING, converters.toStatus("PENDING"))
    }

    @Test
    fun `toStatus converts DONE string back`() {
        assertEquals(TransactionStatus.DONE, converters.toStatus("DONE"))
    }

    @Test
    fun `toStatus converts SKIPPED string back`() {
        assertEquals(TransactionStatus.SKIPPED, converters.toStatus("SKIPPED"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `toStatus throws on invalid string`() {
        converters.toStatus("INVALID")
    }

    @Test
    fun `roundtrip conversion is identity`() {
        TransactionStatus.values().forEach { status ->
            assertEquals(status, converters.toStatus(converters.fromStatus(status)))
        }
    }
}
