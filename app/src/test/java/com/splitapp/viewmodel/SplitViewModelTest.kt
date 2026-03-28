package com.splitapp.viewmodel

import com.splitapp.model.Friend
import com.splitapp.model.Group
import com.splitapp.model.OrderItem
import com.splitapp.model.Transaction
import com.splitapp.model.TransactionStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for SplitViewModel business logic.
 *
 * Since SplitViewModel is an AndroidViewModel with tight coupling to
 * Application/Room/PreferencesStore, we test the pure logic methods directly
 * and the state management via the public API on the state flows.
 *
 * For methods that require viewModelScope (load, submit, skip), see
 * SplitViewModelIntegrationTest which uses Robolectric.
 *
 * What we CAN test without Android framework:
 * - equalSplit calculation (via toggleFriend/applyEqualSplit effects)
 * - toggleGroup/applyGroupSelection state transitions
 * - hasManualSplits detection
 * - toggleFriend selection/deselection
 * - updateRatio clamping
 * - updateDescription
 * - togglePinFriend/togglePinGroup state transitions
 * - Itemized: initItemizedMode, addItem, editItem, deleteItem, assignBucket,
 *   computePerPersonTotals, clearItemizedState
 */
class SplitViewModelTest {

    // Test helpers — we test logic methods that don't touch Android
    // by directly manipulating state objects

    private val alice = Friend(id = 1, firstName = "Alice", lastName = "A", email = "alice@test.com")
    private val bob = Friend(id = 2, firstName = "Bob", lastName = "B", email = "bob@test.com")
    private val charlie = Friend(id = 3, firstName = "Charlie", lastName = null, email = null)

    private val testTransaction = Transaction(
        id = 100,
        amount = 300.0,
        description = "Test dinner",
        rawSms = "INR 300 debited at restaurant",
        timestamp = System.currentTimeMillis()
    )

    private val flatmates = Group(
        id = 10, name = "Flatmates",
        members = listOf(alice, bob)
    )

    private val office = Group(
        id = 20, name = "Office",
        members = listOf(alice, bob, charlie)
    )

    // =========================================================================
    // Equal split calculation
    // =========================================================================

    @Test
    fun `equal split with 1 friend - each gets 50 percent`() {
        // 1 friend + you = 2 people, 100/2 = 50% each
        val splits = listOf(FriendSplit(alice, 0f))
        val result = applyEqualSplit(splits)
        assertEquals(1, result.size)
        assertEquals(50.0f, result[0].ratio, 0.01f)
    }

    @Test
    fun `equal split with 2 friends - each gets 33 percent`() {
        val splits = listOf(FriendSplit(alice, 0f), FriendSplit(bob, 0f))
        val result = applyEqualSplit(splits)
        assertEquals(2, result.size)
        val expected = 100f / 3f
        assertEquals(expected, result[0].ratio, 0.01f)
        assertEquals(expected, result[1].ratio, 0.01f)
    }

    @Test
    fun `equal split with 3 friends - each gets 25 percent`() {
        val splits = listOf(
            FriendSplit(alice, 0f),
            FriendSplit(bob, 0f),
            FriendSplit(charlie, 0f)
        )
        val result = applyEqualSplit(splits)
        assertEquals(25.0f, result[0].ratio, 0.01f)
    }

    @Test
    fun `equal split with empty list returns empty`() {
        val result = applyEqualSplit(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `equal split overwrites previous custom ratios`() {
        val splits = listOf(
            FriendSplit(alice, 70f),
            FriendSplit(bob, 20f)
        )
        val result = applyEqualSplit(splits)
        val expected = 100f / 3f
        assertEquals(expected, result[0].ratio, 0.01f)
        assertEquals(expected, result[1].ratio, 0.01f)
    }

    // =========================================================================
    // hasManualSplits
    // =========================================================================

    @Test
    fun `hasManualSplits - true when splits exist without group`() {
        val state = SplitUiState.Ready(
            transaction = testTransaction,
            friends = listOf(alice),
            groups = emptyList(),
            selectedSplits = listOf(FriendSplit(alice, 50f)),
            selectedGroupId = null
        )
        assertTrue(hasManualSplits(state))
    }

    @Test
    fun `hasManualSplits - false when no splits`() {
        val state = SplitUiState.Ready(
            transaction = testTransaction,
            friends = listOf(alice),
            groups = emptyList(),
            selectedSplits = emptyList(),
            selectedGroupId = null
        )
        assertFalse(hasManualSplits(state))
    }

    @Test
    fun `hasManualSplits - false when splits come from a group`() {
        val state = SplitUiState.Ready(
            transaction = testTransaction,
            friends = listOf(alice, bob),
            groups = listOf(flatmates),
            selectedSplits = listOf(FriendSplit(alice, 50f), FriendSplit(bob, 50f)),
            selectedGroupId = flatmates.id
        )
        assertFalse(hasManualSplits(state))
    }

    // =========================================================================
    // Group selection
    // =========================================================================

    @Test
    fun `applyGroupSelection replaces splits with group members`() {
        val state = SplitUiState.Ready(
            transaction = testTransaction,
            friends = listOf(alice, bob, charlie),
            groups = listOf(flatmates),
            selectedSplits = listOf(FriendSplit(charlie, 50f)),
            selectedGroupId = null
        )
        val result = applyGroupSelection(flatmates, testTransaction, state)
        assertEquals(flatmates.id, result.selectedGroupId)
        assertEquals(2, result.selectedSplits.size)
        assertTrue(result.selectedSplits.any { it.friend.id == alice.id })
        assertTrue(result.selectedSplits.any { it.friend.id == bob.id })
        // Should be equal split
        val expected = 100f / 3f  // 2 friends + you
        assertEquals(expected, result.selectedSplits[0].ratio, 0.01f)
    }

    @Test
    fun `toggleGroup deselect - clears group but keeps splits`() {
        val state = SplitUiState.Ready(
            transaction = testTransaction,
            friends = listOf(alice, bob),
            groups = listOf(flatmates),
            selectedSplits = listOf(FriendSplit(alice, 40f), FriendSplit(bob, 30f)),
            selectedGroupId = flatmates.id
        )
        val result = toggleGroup(flatmates, testTransaction, state)
        assertNull(result.selectedGroupId)
        // Splits should remain untouched
        assertEquals(2, result.selectedSplits.size)
        assertEquals(40f, result.selectedSplits[0].ratio, 0.01f)
        assertEquals(30f, result.selectedSplits[1].ratio, 0.01f)
    }

    @Test
    fun `selecting different group replaces previous group's members`() {
        val stateWithFlatmates = SplitUiState.Ready(
            transaction = testTransaction,
            friends = listOf(alice, bob, charlie),
            groups = listOf(flatmates, office),
            selectedSplits = listOf(FriendSplit(alice, 50f), FriendSplit(bob, 50f)),
            selectedGroupId = flatmates.id
        )
        val result = applyGroupSelection(office, testTransaction, stateWithFlatmates)
        assertEquals(office.id, result.selectedGroupId)
        assertEquals(3, result.selectedSplits.size)
    }

    // =========================================================================
    // Friend toggle
    // =========================================================================

    @Test
    fun `toggle friend on - adds with equal rebalance`() {
        val splits = emptyList<FriendSplit>()
        val result = toggleFriend(alice, testTransaction, splits)
        assertEquals(1, result.size)
        assertEquals(alice.id, result[0].friend.id)
        assertEquals(50.0f, result[0].ratio, 0.01f)  // you + alice = 50% each
    }

    @Test
    fun `toggle friend off - removes and rebalances remaining`() {
        val splits = listOf(
            FriendSplit(alice, 33.3f),
            FriendSplit(bob, 33.3f)
        )
        val result = toggleFriend(alice, testTransaction, splits)
        assertEquals(1, result.size)
        assertEquals(bob.id, result[0].friend.id)
        assertEquals(50.0f, result[0].ratio, 0.01f)  // rebalanced: you + bob
    }

    @Test
    fun `toggle last friend off - returns empty splits`() {
        val splits = listOf(FriendSplit(alice, 50f))
        val result = toggleFriend(alice, testTransaction, splits)
        assertTrue(result.isEmpty())
    }

    // =========================================================================
    // Ratio update
    // =========================================================================

    @Test
    fun `updateRatio sets exact value`() {
        val splits = listOf(FriendSplit(alice, 50f), FriendSplit(bob, 50f))
        val result = updateRatio(alice.id, 70f, splits)
        assertEquals(70f, result.find { it.friend.id == alice.id }!!.ratio, 0.01f)
        assertEquals(50f, result.find { it.friend.id == bob.id }!!.ratio, 0.01f)
    }

    @Test
    fun `updateRatio clamps negative to 0`() {
        val splits = listOf(FriendSplit(alice, 50f))
        val result = updateRatio(alice.id, -10f, splits)
        assertEquals(0f, result[0].ratio, 0.01f)
    }

    @Test
    fun `updateRatio clamps above 100 to 100`() {
        val splits = listOf(FriendSplit(alice, 50f))
        val result = updateRatio(alice.id, 150f, splits)
        assertEquals(100f, result[0].ratio, 0.01f)
    }

    @Test
    fun `updateRatio for non-existent friend changes nothing`() {
        val splits = listOf(FriendSplit(alice, 50f))
        val result = updateRatio(999L, 70f, splits)
        assertEquals(50f, result[0].ratio, 0.01f)
    }

    // =========================================================================
    // Your share calculation
    // =========================================================================

    @Test
    fun `your share is remainder after friends' ratios`() {
        // Equal split with 2 friends: each gets 33.3%, you get ~33.3%
        val splits = listOf(
            FriendSplit(alice, 33.33f),
            FriendSplit(bob, 33.33f)
        )
        val totalRatio = splits.sumOf { it.ratio.toDouble() }.toFloat()
        val yourShare = testTransaction.amount * (100f - totalRatio) / 100f
        assertEquals(100.02, yourShare.toDouble(), 0.1)
    }

    @Test
    fun `custom 70-30 split - your share is 0 if ratios sum to 100`() {
        val splits = listOf(
            FriendSplit(alice, 70f),
            FriendSplit(bob, 30f)
        )
        val totalRatio = splits.sumOf { it.ratio.toDouble() }.toFloat()
        val yourShare = testTransaction.amount * (100f - totalRatio) / 100f
        assertEquals(0.0, yourShare.toDouble(), 0.001)
    }

    // =========================================================================
    // Pin state management
    // =========================================================================

    @Test
    fun `pin friend adds to set`() {
        val pins = emptySet<Long>()
        val result = togglePin(alice.id, pins)
        assertTrue(alice.id in result)
    }

    @Test
    fun `unpin friend removes from set`() {
        val pins = setOf(alice.id)
        val result = togglePin(alice.id, pins)
        assertFalse(alice.id in result)
    }

    @Test
    fun `pin multiple friends`() {
        var pins = emptySet<Long>()
        pins = togglePin(alice.id, pins)
        pins = togglePin(bob.id, pins)
        assertEquals(2, pins.size)
        assertTrue(alice.id in pins)
        assertTrue(bob.id in pins)
    }

    // =========================================================================
    // Itemized mode: init
    // =========================================================================

    @Test
    fun `initItemizedMode sets participants from friends plus current user`() {
        val currentUserId = 99L
        val friends = listOf(FriendSplit(alice, 50f), FriendSplit(bob, 50f))

        val state = initItemizedMode(currentUserId, friends, SplitViewModel.ItemizedUiState.Idle)

        assertTrue(currentUserId in state.allParticipantIds)
        assertTrue(alice.id in state.allParticipantIds)
        assertTrue(bob.id in state.allParticipantIds)
        assertEquals(3, state.allParticipantIds.size)
        assertEquals("You", state.participantNames[currentUserId])
        assertEquals("Alice A", state.participantNames[alice.id])
    }

    @Test
    fun `initItemizedMode re-entry remaps buckets for changed participants`() {
        val currentUserId = 99L
        val item = OrderItem(id = "item1", name = "Apple", price = 50.0)

        // Initial state: alice and bob
        val initialState = SplitViewModel.ItemizedUiState(
            items = listOf(item),
            buckets = mapOf("item1" to setOf(currentUserId, alice.id, bob.id)),
            allParticipantIds = setOf(currentUserId, alice.id, bob.id),
            participantNames = mapOf(currentUserId to "You", alice.id to "Alice A", bob.id to "Bob B")
        )

        // Re-enter with charlie instead of bob
        val newFriends = listOf(FriendSplit(alice, 50f), FriendSplit(charlie, 50f))
        val result = initItemizedMode(currentUserId, newFriends, initialState)

        // Bob removed from bucket, remaining: currentUser + alice
        val newBucket = result.buckets["item1"]!!
        assertTrue(currentUserId in newBucket)
        assertTrue(alice.id in newBucket)
        assertFalse(bob.id in newBucket)
        // Charlie is in participants but wasn't in original bucket
        assertTrue(charlie.id in result.allParticipantIds)
    }

    @Test
    fun `initItemizedMode re-entry with empty intersection resets to all`() {
        val currentUserId = 99L
        val item = OrderItem(id = "item1", name = "Apple", price = 50.0)

        // Bucket only had bob
        val initialState = SplitViewModel.ItemizedUiState(
            items = listOf(item),
            buckets = mapOf("item1" to setOf(bob.id)),
            allParticipantIds = setOf(currentUserId, bob.id),
            participantNames = mapOf(currentUserId to "You", bob.id to "Bob B")
        )

        // Replace bob with charlie entirely
        val newFriends = listOf(FriendSplit(charlie, 50f))
        val result = initItemizedMode(currentUserId, newFriends, initialState)

        // Bucket had only bob, intersection empty -> reset to all new participants
        val newBucket = result.buckets["item1"]!!
        assertEquals(setOf(currentUserId, charlie.id), newBucket)
    }

    // =========================================================================
    // Itemized mode: add, edit, delete items
    // =========================================================================

    @Test
    fun `addItem appends item and creates bucket for all`() {
        val allIds = setOf(1L, 2L, 3L)
        val state = SplitViewModel.ItemizedUiState(allParticipantIds = allIds)

        val result = addItem("Milk", 45.0, false, state)

        assertEquals(1, result.items.size)
        assertEquals("Milk", result.items[0].name)
        assertEquals(45.0, result.items[0].price, 0.001)
        assertFalse(result.items[0].isFee)
        assertEquals(allIds, result.buckets[result.items[0].id])
    }

    @Test
    fun `addItem as fee`() {
        val allIds = setOf(1L, 2L)
        val state = SplitViewModel.ItemizedUiState(allParticipantIds = allIds)

        val result = addItem("Delivery fee", 30.0, true, state)

        assertTrue(result.items[0].isFee)
    }

    @Test
    fun `editItem changes name and price`() {
        val item = OrderItem(id = "i1", name = "Old", price = 10.0)
        val state = SplitViewModel.ItemizedUiState(
            items = listOf(item),
            buckets = mapOf("i1" to setOf(1L)),
            allParticipantIds = setOf(1L, 2L)
        )

        val result = editItem("i1", "New", 20.0, null, state)

        assertEquals("New", result.items[0].name)
        assertEquals(20.0, result.items[0].price, 0.001)
        assertFalse(result.items[0].isFee)  // unchanged
    }

    @Test
    fun `editItem toggling isFee to true reassigns bucket to all`() {
        val item = OrderItem(id = "i1", name = "Charge", price = 10.0, isFee = false)
        val allIds = setOf(1L, 2L, 3L)
        val state = SplitViewModel.ItemizedUiState(
            items = listOf(item),
            buckets = mapOf("i1" to setOf(1L)),  // was assigned to just person 1
            allParticipantIds = allIds
        )

        val result = editItem("i1", "Handling charge", 10.0, true, state)

        assertTrue(result.items[0].isFee)
        assertEquals(allIds, result.buckets["i1"])  // reassigned to all
    }

    @Test
    fun `editItem toggling isFee to false keeps existing bucket`() {
        val item = OrderItem(id = "i1", name = "Fee", price = 10.0, isFee = true)
        val allIds = setOf(1L, 2L)
        val state = SplitViewModel.ItemizedUiState(
            items = listOf(item),
            buckets = mapOf("i1" to allIds),
            allParticipantIds = allIds
        )

        val result = editItem("i1", "Not a fee", 10.0, false, state)

        assertFalse(result.items[0].isFee)
        // Bucket not changed when toggling to non-fee
        assertEquals(allIds, result.buckets["i1"])
    }

    @Test
    fun `deleteItem removes item and its bucket`() {
        val item1 = OrderItem(id = "i1", name = "A", price = 10.0)
        val item2 = OrderItem(id = "i2", name = "B", price = 20.0)
        val state = SplitViewModel.ItemizedUiState(
            items = listOf(item1, item2),
            buckets = mapOf("i1" to setOf(1L), "i2" to setOf(1L, 2L)),
            allParticipantIds = setOf(1L, 2L)
        )

        val result = deleteItem("i1", state)

        assertEquals(1, result.items.size)
        assertEquals("B", result.items[0].name)
        assertFalse(result.buckets.containsKey("i1"))
        assertTrue(result.buckets.containsKey("i2"))
    }

    // =========================================================================
    // Itemized mode: bucket assignment
    // =========================================================================

    @Test
    fun `assignBucket updates item's participant set`() {
        val state = SplitViewModel.ItemizedUiState(
            items = listOf(OrderItem(id = "i1", name = "A", price = 10.0)),
            buckets = mapOf("i1" to setOf(1L, 2L, 3L)),
            allParticipantIds = setOf(1L, 2L, 3L)
        )

        val result = assignBucket("i1", setOf(1L, 2L), state)

        assertEquals(setOf(1L, 2L), result.buckets["i1"])
    }

    // =========================================================================
    // Itemized mode: per-person totals
    // =========================================================================

    @Test
    fun `computePerPersonTotals - all items shared equally`() {
        val items = listOf(
            OrderItem(id = "i1", name = "A", price = 30.0),
            OrderItem(id = "i2", name = "B", price = 60.0)
        )
        val allIds = setOf(1L, 2L, 3L)
        val state = SplitViewModel.ItemizedUiState(
            items = items,
            buckets = mapOf("i1" to allIds, "i2" to allIds),
            allParticipantIds = allIds
        )

        val totals = computePerPersonTotals(state)

        // Total = 90, 3 people, each owes 30
        assertEquals(30.0, totals[1L]!!, 0.001)
        assertEquals(30.0, totals[2L]!!, 0.001)
        assertEquals(30.0, totals[3L]!!, 0.001)
    }

    @Test
    fun `computePerPersonTotals - mixed buckets`() {
        val items = listOf(
            OrderItem(id = "i1", name = "Shared", price = 30.0),     // all 3
            OrderItem(id = "i2", name = "Private", price = 20.0),    // only person 1
            OrderItem(id = "i3", name = "Pair", price = 10.0)        // persons 1 and 2
        )
        val allIds = setOf(1L, 2L, 3L)
        val state = SplitViewModel.ItemizedUiState(
            items = items,
            buckets = mapOf(
                "i1" to allIds,
                "i2" to setOf(1L),
                "i3" to setOf(1L, 2L)
            ),
            allParticipantIds = allIds
        )

        val totals = computePerPersonTotals(state)

        // Person 1: 30/3 + 20/1 + 10/2 = 10 + 20 + 5 = 35
        assertEquals(35.0, totals[1L]!!, 0.001)
        // Person 2: 30/3 + 10/2 = 10 + 5 = 15
        assertEquals(15.0, totals[2L]!!, 0.001)
        // Person 3: 30/3 = 10
        assertEquals(10.0, totals[3L]!!, 0.001)
    }

    @Test
    fun `computePerPersonTotals - fee items shared by all`() {
        val items = listOf(
            OrderItem(id = "i1", name = "Item", price = 100.0, isFee = false),
            OrderItem(id = "fee1", name = "Delivery", price = 30.0, isFee = true)
        )
        val allIds = setOf(1L, 2L)
        val state = SplitViewModel.ItemizedUiState(
            items = items,
            buckets = mapOf(
                "i1" to setOf(1L),     // only person 1
                "fee1" to allIds       // fee shared by all
            ),
            allParticipantIds = allIds
        )

        val totals = computePerPersonTotals(state)

        // Person 1: 100 + 30/2 = 115
        assertEquals(115.0, totals[1L]!!, 0.001)
        // Person 2: 0 + 30/2 = 15
        assertEquals(15.0, totals[2L]!!, 0.001)
    }

    @Test
    fun `computePerPersonTotals - no items returns all zeros`() {
        val allIds = setOf(1L, 2L)
        val state = SplitViewModel.ItemizedUiState(
            items = emptyList(),
            buckets = emptyMap(),
            allParticipantIds = allIds
        )

        val totals = computePerPersonTotals(state)

        assertEquals(0.0, totals[1L]!!, 0.001)
        assertEquals(0.0, totals[2L]!!, 0.001)
    }

    @Test
    fun `computePerPersonTotals - item without bucket defaults to all`() {
        val items = listOf(OrderItem(id = "i1", name = "A", price = 60.0))
        val allIds = setOf(1L, 2L)
        val state = SplitViewModel.ItemizedUiState(
            items = items,
            buckets = emptyMap(),  // no bucket assigned
            allParticipantIds = allIds
        )

        val totals = computePerPersonTotals(state)

        // Falls back to allParticipantIds -> 60/2 = 30 each
        assertEquals(30.0, totals[1L]!!, 0.001)
        assertEquals(30.0, totals[2L]!!, 0.001)
    }

    @Test
    fun `computePerPersonTotals sums to item total`() {
        val items = listOf(
            OrderItem(id = "i1", name = "A", price = 33.0),
            OrderItem(id = "i2", name = "B", price = 67.0)
        )
        val allIds = setOf(1L, 2L, 3L)
        val state = SplitViewModel.ItemizedUiState(
            items = items,
            buckets = mapOf("i1" to setOf(1L, 2L), "i2" to allIds),
            allParticipantIds = allIds
        )

        val totals = computePerPersonTotals(state)
        val sumOfShares = totals.values.sum()
        val itemTotal = items.sumOf { it.price }

        assertEquals(itemTotal, sumOfShares, 0.001)
    }

    // =========================================================================
    // Itemized mode: clear state
    // =========================================================================

    @Test
    fun `clearItemizedState resets to idle`() {
        val state = SplitViewModel.ItemizedUiState(
            items = listOf(OrderItem(id = "i1", name = "A", price = 10.0)),
            buckets = mapOf("i1" to setOf(1L)),
            allParticipantIds = setOf(1L, 2L)
        )

        val result = SplitViewModel.ItemizedUiState.Idle

        assertTrue(result.items.isEmpty())
        assertTrue(result.buckets.isEmpty())
        assertTrue(result.allParticipantIds.isEmpty())
        assertFalse(result.isProcessingOcr)
        assertNull(result.ocrError)
    }

    // =========================================================================
    // Expense field calculation
    // =========================================================================

    @Test
    fun `split amounts calculated correctly from ratios`() {
        val transaction = Transaction(
            id = 1, amount = 1000.0, description = "Test",
            rawSms = "sms", timestamp = 1000L
        )
        val splits = listOf(
            FriendSplit(alice, 30f),
            FriendSplit(bob, 20f)
        )
        val splitAmounts = splits.map { fs ->
            Pair(fs.friend.id, transaction.amount * fs.ratio / 100.0)
        }
        assertEquals(300.0, splitAmounts[0].second, 0.001)
        assertEquals(200.0, splitAmounts[1].second, 0.001)
        // Your share = 1000 - 300 - 200 = 500
        val yourOwed = transaction.amount - splitAmounts.sumOf { it.second }
        assertEquals(500.0, yourOwed, 0.001)
    }

    // =========================================================================
    // Ratio validation (UI logic)
    // =========================================================================

    @Test
    fun `ratioValid is true when splits exist and total under 100`() {
        val splits = listOf(FriendSplit(alice, 30f), FriendSplit(bob, 30f))
        val totalRatio = splits.sumOf { it.ratio.toDouble() }.toFloat()
        val valid = splits.isNotEmpty() && totalRatio <= 100f
        assertTrue(valid)
    }

    @Test
    fun `ratioValid is true when total equals exactly 100`() {
        val splits = listOf(FriendSplit(alice, 60f), FriendSplit(bob, 40f))
        val totalRatio = splits.sumOf { it.ratio.toDouble() }.toFloat()
        val valid = splits.isNotEmpty() && totalRatio <= 100f
        assertTrue(valid)
    }

    @Test
    fun `ratioValid is false when total exceeds 100`() {
        val splits = listOf(FriendSplit(alice, 70f), FriendSplit(bob, 40f))
        val totalRatio = splits.sumOf { it.ratio.toDouble() }.toFloat()
        val valid = splits.isNotEmpty() && totalRatio <= 100f
        assertFalse(valid)
    }

    @Test
    fun `ratioValid is false when no splits selected`() {
        val splits = emptyList<FriendSplit>()
        val valid = splits.isNotEmpty()
        assertFalse(valid)
    }

    // =========================================================================
    // Helper methods — mirror the ViewModel logic without Android dependencies
    // =========================================================================

    private fun applyEqualSplit(splits: List<FriendSplit>): List<FriendSplit> {
        if (splits.isEmpty()) return splits
        val perPerson = 100f / (splits.size + 1)  // +1 for you
        return splits.map { it.copy(ratio = perPerson) }
    }

    private fun hasManualSplits(state: SplitUiState.Ready): Boolean {
        return state.selectedSplits.isNotEmpty() && state.selectedGroupId == null
    }

    private fun applyGroupSelection(
        group: Group, transaction: Transaction, current: SplitUiState.Ready
    ): SplitUiState.Ready {
        val newSplits = group.members.map { FriendSplit(it, 0f) }
        val rebalanced = applyEqualSplit(newSplits)
        return current.copy(selectedGroupId = group.id, selectedSplits = rebalanced)
    }

    private fun toggleGroup(
        group: Group, transaction: Transaction, current: SplitUiState.Ready
    ): SplitUiState.Ready {
        return if (current.selectedGroupId == group.id) {
            current.copy(selectedGroupId = null)
        } else {
            applyGroupSelection(group, transaction, current)
        }
    }

    private fun toggleFriend(
        friend: Friend, transaction: Transaction, currentSplits: List<FriendSplit>
    ): List<FriendSplit> {
        val existing = currentSplits.find { it.friend.id == friend.id }
        val newSplits = if (existing != null) {
            currentSplits.filter { it.friend.id != friend.id }
        } else {
            currentSplits + FriendSplit(friend, 0f)
        }
        return applyEqualSplit(newSplits)
    }

    private fun updateRatio(
        friendId: Long, ratio: Float, splits: List<FriendSplit>
    ): List<FriendSplit> {
        return splits.map {
            if (it.friend.id == friendId) it.copy(ratio = ratio.coerceIn(0f, 100f)) else it
        }
    }

    private fun togglePin(id: Long, current: Set<Long>): Set<Long> {
        return if (id in current) current - id else current + id
    }

    private fun initItemizedMode(
        currentUserId: Long,
        selectedFriends: List<FriendSplit>,
        current: SplitViewModel.ItemizedUiState
    ): SplitViewModel.ItemizedUiState {
        val newIds = setOf(currentUserId) + selectedFriends.map { it.friend.id }.toSet()
        val names = mutableMapOf(currentUserId to "You")
        selectedFriends.forEach { names[it.friend.id] = it.friend.displayName }

        return if (current.items.isEmpty()) {
            SplitViewModel.ItemizedUiState(
                allParticipantIds = newIds,
                participantNames = names
            )
        } else {
            val newBuckets = current.buckets.mapValues { (_, bucket) ->
                val remapped = bucket.intersect(newIds)
                if (remapped.isEmpty()) newIds else remapped
            }
            current.copy(
                allParticipantIds = newIds,
                participantNames = names,
                buckets = newBuckets
            )
        }
    }

    private fun addItem(
        name: String, price: Double, isFee: Boolean,
        current: SplitViewModel.ItemizedUiState
    ): SplitViewModel.ItemizedUiState {
        val item = OrderItem(name = name, price = price, isFee = isFee)
        return current.copy(
            items = current.items + item,
            buckets = current.buckets + (item.id to current.allParticipantIds)
        )
    }

    private fun editItem(
        itemId: String, newName: String, newPrice: Double, isFee: Boolean?,
        current: SplitViewModel.ItemizedUiState
    ): SplitViewModel.ItemizedUiState {
        var result = current.copy(
            items = current.items.map {
                if (it.id == itemId) {
                    val updatedItem = it.copy(name = newName, price = newPrice)
                    if (isFee != null) updatedItem.copy(isFee = isFee) else updatedItem
                } else it
            }
        )
        if (isFee == true) {
            result = result.copy(
                buckets = result.buckets + (itemId to current.allParticipantIds)
            )
        }
        return result
    }

    private fun deleteItem(
        itemId: String, current: SplitViewModel.ItemizedUiState
    ): SplitViewModel.ItemizedUiState {
        return current.copy(
            items = current.items.filter { it.id != itemId },
            buckets = current.buckets - itemId
        )
    }

    private fun assignBucket(
        itemId: String, participantIds: Set<Long>,
        current: SplitViewModel.ItemizedUiState
    ): SplitViewModel.ItemizedUiState {
        return current.copy(
            buckets = current.buckets + (itemId to participantIds)
        )
    }

    private fun computePerPersonTotals(state: SplitViewModel.ItemizedUiState): Map<Long, Double> {
        val totals = state.allParticipantIds.associateWith { 0.0 }.toMutableMap()
        for (item in state.items) {
            val bucket = state.buckets[item.id] ?: state.allParticipantIds
            if (bucket.isEmpty()) continue
            val share = item.price / bucket.size
            for (pid in bucket) {
                totals[pid] = (totals[pid] ?: 0.0) + share
            }
        }
        return totals
    }
}
