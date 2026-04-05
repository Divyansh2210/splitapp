package com.splitapp.api

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for Splitwise expense field map construction.
 *
 * The repository builds a form-encoded field map for the create_expense endpoint.
 * This tests the field mapping logic without hitting the network.
 */
class SplitwiseRepositoryTest {

    // =========================================================================
    // Expense field map construction
    // =========================================================================

    @Test
    fun `expense fields include cost and description`() {
        val fields = buildExpenseFields(
            totalAmount = 300.0,
            description = "Dinner",
            currentUserId = 99L,
            splits = listOf(Pair(1L, 100.0), Pair(2L, 100.0)),
            groupId = null
        )
        assertEquals("300.00", fields["cost"])
        assertEquals("Dinner", fields["description"])
        assertEquals("false", fields["split_equally"])
    }

    @Test
    fun `expense fields include group_id when provided`() {
        val fields = buildExpenseFields(
            totalAmount = 100.0, description = "Test",
            currentUserId = 99L, splits = listOf(Pair(1L, 50.0)),
            groupId = 42L
        )
        assertEquals("42", fields["group_id"])
    }

    @Test
    fun `expense fields omit group_id when null`() {
        val fields = buildExpenseFields(
            totalAmount = 100.0, description = "Test",
            currentUserId = 99L, splits = listOf(Pair(1L, 50.0)),
            groupId = null
        )
        assertFalse(fields.containsKey("group_id"))
    }

    @Test
    fun `current user is user 0 and pays full amount`() {
        val fields = buildExpenseFields(
            totalAmount = 300.0, description = "Test",
            currentUserId = 99L,
            splits = listOf(Pair(1L, 100.0), Pair(2L, 100.0)),
            groupId = null
        )
        assertEquals("99", fields["users__0__user_id"])
        assertEquals("300.00", fields["users__0__paid_share"])
        assertEquals("100.00", fields["users__0__owed_share"])  // 300 - 100 - 100
    }

    @Test
    fun `friends are indexed starting at 1`() {
        val fields = buildExpenseFields(
            totalAmount = 300.0, description = "Test",
            currentUserId = 99L,
            splits = listOf(Pair(1L, 100.0), Pair(2L, 100.0)),
            groupId = null
        )
        assertEquals("1", fields["users__1__user_id"])
        assertEquals("0.00", fields["users__1__paid_share"])
        assertEquals("100.00", fields["users__1__owed_share"])

        assertEquals("2", fields["users__2__user_id"])
        assertEquals("0.00", fields["users__2__paid_share"])
        assertEquals("100.00", fields["users__2__owed_share"])
    }

    @Test
    fun `your owed share is total minus friends total`() {
        val fields = buildExpenseFields(
            totalAmount = 1000.0, description = "Test",
            currentUserId = 99L,
            splits = listOf(Pair(1L, 300.0), Pair(2L, 200.0)),
            groupId = null
        )
        assertEquals("500.00", fields["users__0__owed_share"])  // 1000 - 300 - 200
    }

    @Test
    fun `single friend split`() {
        val fields = buildExpenseFields(
            totalAmount = 100.0, description = "Coffee",
            currentUserId = 99L,
            splits = listOf(Pair(1L, 50.0)),
            groupId = null
        )
        assertEquals("50.00", fields["users__0__owed_share"])
        assertEquals("50.00", fields["users__1__owed_share"])
    }

    @Test
    fun `you owe nothing when friends cover the full amount`() {
        val fields = buildExpenseFields(
            totalAmount = 100.0, description = "Test",
            currentUserId = 99L,
            splits = listOf(Pair(1L, 60.0), Pair(2L, 40.0)),
            groupId = null
        )
        assertEquals("0.00", fields["users__0__owed_share"])
    }

    @Test
    fun `expense with edited description`() {
        val fields = buildExpenseFields(
            totalAmount = 500.0, description = "Swiggy dinner",
            currentUserId = 99L,
            splits = listOf(Pair(1L, 250.0)),
            groupId = null
        )
        assertEquals("Swiggy dinner", fields["description"])
    }

    @Test
    fun `amount formatting uses 2 decimal places`() {
        val fields = buildExpenseFields(
            totalAmount = 99.9, description = "Test",
            currentUserId = 99L,
            splits = listOf(Pair(1L, 33.3)),
            groupId = null
        )
        assertEquals("99.90", fields["cost"])
        assertEquals("33.30", fields["users__1__owed_share"])
    }

    @Test
    fun `expense fields include details when provided`() {
        val fields = buildExpenseFields(
            totalAmount = 300.0, description = "Groceries",
            currentUserId = 99L, splits = listOf(Pair(1L, 100.0)),
            groupId = null, details = "Item breakdown:\n• Milk — ₹50.00 → Everyone"
        )
        assertEquals("Item breakdown:\n• Milk — ₹50.00 → Everyone", fields["details"])
    }

    @Test
    fun `expense fields omit details when null`() {
        val fields = buildExpenseFields(
            totalAmount = 100.0, description = "Test",
            currentUserId = 99L, splits = listOf(Pair(1L, 50.0)),
            groupId = null, details = null
        )
        assertFalse(fields.containsKey("details"))
    }

    // =========================================================================
    // Helper — mirrors SplitwiseRepository.createExpense field building
    // =========================================================================

    private fun buildExpenseFields(
        totalAmount: Double,
        description: String,
        currentUserId: Long,
        splits: List<Pair<Long, Double>>,
        groupId: Long?,
        details: String? = null
    ): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        fields["cost"] = "%.2f".format(totalAmount)
        fields["description"] = description
        fields["split_equally"] = "false"
        groupId?.let { fields["group_id"] = it.toString() }
        details?.let { fields["details"] = it }

        // Format each friend's share, then derive payer's share so the total is exact
        val formattedSplits = splits.map { (friendId, owedAmount) ->
            Triple(friendId, "0.00", "%.2f".format(owedAmount))
        }
        val friendsTotal = formattedSplits.sumOf { it.third.toDouble() }
        val yourOwed = "%.2f".format(totalAmount - friendsTotal)

        fields["users__0__user_id"] = currentUserId.toString()
        fields["users__0__paid_share"] = "%.2f".format(totalAmount)
        fields["users__0__owed_share"] = yourOwed

        formattedSplits.forEachIndexed { index, (friendId, paidShare, owedShare) ->
            val i = index + 1
            fields["users__${i}__user_id"] = friendId.toString()
            fields["users__${i}__paid_share"] = paidShare
            fields["users__${i}__owed_share"] = owedShare
        }
        return fields
    }
}
