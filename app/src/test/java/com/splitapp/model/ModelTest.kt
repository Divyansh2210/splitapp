package com.splitapp.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for data model correctness — display names, default values, status enum.
 */
class ModelTest {

    // =========================================================================
    // Friend display name
    // =========================================================================

    @Test
    fun `displayName with first and last name`() {
        val friend = Friend(id = 1, firstName = "Aman", lastName = "Gupta", email = "aman@mail.com")
        assertEquals("Aman Gupta", friend.displayName)
    }

    @Test
    fun `displayName with first name only`() {
        val friend = Friend(id = 1, firstName = "Aman", lastName = null, email = null)
        assertEquals("Aman", friend.displayName)
    }

    @Test
    fun `displayName with empty last name is treated as non-null`() {
        // empty string is NOT null, so it joins
        val friend = Friend(id = 1, firstName = "Aman", lastName = "", email = null)
        assertEquals("Aman ", friend.displayName)
    }

    // =========================================================================
    // Transaction defaults
    // =========================================================================

    @Test
    fun `transaction default status is PENDING`() {
        val tx = Transaction(
            amount = 100.0,
            description = "Test",
            rawSms = "test sms",
            timestamp = System.currentTimeMillis()
        )
        assertEquals(TransactionStatus.PENDING, tx.status)
    }

    @Test
    fun `transaction default id is 0`() {
        val tx = Transaction(
            amount = 50.0,
            description = "Test",
            rawSms = "test",
            timestamp = 1000L
        )
        assertEquals(0L, tx.id)
    }

    // =========================================================================
    // TransactionStatus enum
    // =========================================================================

    @Test
    fun `all expected statuses exist`() {
        val statuses = TransactionStatus.values()
        assertEquals(3, statuses.size)
        assertTrue(statuses.contains(TransactionStatus.PENDING))
        assertTrue(statuses.contains(TransactionStatus.DONE))
        assertTrue(statuses.contains(TransactionStatus.SKIPPED))
    }

    // =========================================================================
    // OrderItem defaults
    // =========================================================================

    @Test
    fun `OrderItem generates unique IDs`() {
        val item1 = OrderItem(name = "Apple", price = 50.0)
        val item2 = OrderItem(name = "Banana", price = 30.0)
        assertNotEquals(item1.id, item2.id)
    }

    @Test
    fun `OrderItem default isFee is false`() {
        val item = OrderItem(name = "Milk", price = 45.0)
        assertFalse(item.isFee)
    }

    @Test
    fun `OrderItem can be created as fee`() {
        val item = OrderItem(name = "Delivery fee", price = 30.0, isFee = true)
        assertTrue(item.isFee)
    }

    // =========================================================================
    // Group
    // =========================================================================

    @Test
    fun `group with empty members list`() {
        val group = Group(id = 1, name = "Test Group", members = emptyList())
        assertTrue(group.members.isEmpty())
    }

    @Test
    fun `group members are friends`() {
        val f1 = Friend(1, "A", "B", null)
        val f2 = Friend(2, "C", "D", "c@d.com")
        val group = Group(id = 10, name = "Flat", members = listOf(f1, f2))
        assertEquals(2, group.members.size)
        assertEquals("A B", group.members[0].displayName)
    }
}
