package com.splitapp.viewmodel

import com.splitapp.model.Friend
import com.splitapp.model.Group
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for friend/group search, filtering, and pin-based sorting.
 *
 * These mirror the display logic in SplitScreen's remember{} blocks.
 */
class FriendSearchAndSortTest {

    private val alice = Friend(1, "Alice", "Anderson", "alice@test.com")
    private val aman = Friend(2, "Aman", "Gupta", "aman@test.com")
    private val bob = Friend(3, "Bob", "Builder", "bob@test.com")
    private val charlie = Friend(4, "Charlie", "Chaplin", null)

    private val allFriends = listOf(alice, aman, bob, charlie)

    // =========================================================================
    // Friend search
    // =========================================================================

    @Test
    fun `search by first name - case insensitive`() {
        val results = filterFriends(allFriends, "aman", emptySet(), emptySet())
        assertEquals(1, results.size)
        assertEquals(aman.id, results[0].id)
    }

    @Test
    fun `search by partial name`() {
        val results = filterFriends(allFriends, "ali", emptySet(), emptySet())
        assertEquals(1, results.size)
        assertEquals(alice.id, results[0].id)
    }

    @Test
    fun `search by email`() {
        val results = filterFriends(allFriends, "bob@test", emptySet(), emptySet())
        assertEquals(1, results.size)
        assertEquals(bob.id, results[0].id)
    }

    @Test
    fun `search with no match returns empty`() {
        val results = filterFriends(allFriends, "xyz", emptySet(), emptySet())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `blank search returns all unselected friends`() {
        val results = filterFriends(allFriends, "", emptySet(), emptySet())
        assertEquals(4, results.size)
    }

    @Test
    fun `selected friends always visible regardless of search`() {
        val selectedIds = setOf(alice.id)
        val results = filterFriends(allFriends, "bob", selectedIds, emptySet())
        // Should show: alice (selected, always visible) + bob (matches search)
        assertEquals(2, results.size)
        assertTrue(results.any { it.id == alice.id })
        assertTrue(results.any { it.id == bob.id })
    }

    @Test
    fun `search matches display name (first + last)`() {
        val results = filterFriends(allFriends, "Anderson", emptySet(), emptySet())
        assertEquals(1, results.size)
        assertEquals(alice.id, results[0].id)
    }

    @Test
    fun `friend with null email does not crash on email search`() {
        val results = filterFriends(allFriends, "@test", emptySet(), emptySet())
        // Charlie has null email, should not crash, should find alice, aman, bob
        assertEquals(3, results.size)
    }

    // =========================================================================
    // Friend sorting — selected first, then pinned
    // =========================================================================

    @Test
    fun `selected friends appear before unselected`() {
        val selectedIds = setOf(bob.id)
        val results = filterFriends(allFriends, "", selectedIds, emptySet())
        assertEquals(bob.id, results[0].id)
    }

    @Test
    fun `pinned friends appear first within their section`() {
        val pinnedIds = setOf(charlie.id)
        val results = filterFriends(allFriends, "", emptySet(), pinnedIds)
        assertEquals(charlie.id, results[0].id)
    }

    @Test
    fun `pinned selected friend appears first in selected section`() {
        val selectedIds = setOf(alice.id, bob.id)
        val pinnedIds = setOf(bob.id)
        val results = filterFriends(allFriends, "", selectedIds, pinnedIds)
        // bob is selected + pinned, should be first
        assertEquals(bob.id, results[0].id)
        assertEquals(alice.id, results[1].id)
    }

    @Test
    fun `pinned unselected friend appears first in unselected section`() {
        val selectedIds = setOf(alice.id)
        val pinnedIds = setOf(charlie.id)
        val results = filterFriends(allFriends, "", selectedIds, pinnedIds)
        // alice first (selected), then charlie (pinned unselected)
        assertEquals(alice.id, results[0].id)
        assertEquals(charlie.id, results[1].id)
    }

    // =========================================================================
    // Group search
    // =========================================================================

    @Test
    fun `group search by name`() {
        val groups = listOf(
            Group(1, "Flatmates", emptyList()),
            Group(2, "Office Friends", emptyList()),
            Group(3, "Family", emptyList())
        )
        val results = filterGroups(groups, "flat", null, emptySet())
        assertEquals(1, results.size)
        assertEquals("Flatmates", results[0].name)
    }

    @Test
    fun `selected group always visible regardless of search`() {
        val groups = listOf(
            Group(1, "Flatmates", emptyList()),
            Group(2, "Office", emptyList())
        )
        val results = filterGroups(groups, "office", 1L, emptySet())
        // Flatmates (selected, always visible) + Office (matches search)
        assertEquals(2, results.size)
    }

    @Test
    fun `pinned groups appear first`() {
        val groups = listOf(
            Group(1, "Alpha", emptyList()),
            Group(2, "Beta", emptyList()),
            Group(3, "Gamma", emptyList())
        )
        val results = filterGroups(groups, "", null, setOf(3L))
        assertEquals(3L, results[0].id)
    }

    @Test
    fun `blank group search returns all groups`() {
        val groups = listOf(
            Group(1, "A", emptyList()),
            Group(2, "B", emptyList())
        )
        val results = filterGroups(groups, "", null, emptySet())
        assertEquals(2, results.size)
    }

    // =========================================================================
    // Helpers — mirror SplitScreen display logic
    // =========================================================================

    private fun filterFriends(
        friends: List<Friend>,
        searchQuery: String,
        selectedIds: Set<Long>,
        pinnedIds: Set<Long>
    ): List<Friend> {
        val selected = friends.filter { it.id in selectedIds }
            .sortedByDescending { it.id in pinnedIds }
        val unselected = friends
            .filter { it.id !in selectedIds }
            .filter { friend ->
                searchQuery.isBlank() ||
                    friend.displayName.contains(searchQuery, ignoreCase = true) ||
                    friend.email?.contains(searchQuery, ignoreCase = true) == true
            }
            .sortedByDescending { it.id in pinnedIds }
        return selected + unselected
    }

    private fun filterGroups(
        groups: List<Group>,
        searchQuery: String,
        selectedGroupId: Long?,
        pinnedIds: Set<Long>
    ): List<Group> {
        return groups.filter { group ->
            group.id == selectedGroupId ||
                searchQuery.isBlank() ||
                group.name.contains(searchQuery, ignoreCase = true)
        }.sortedByDescending { it.id in pinnedIds }
    }
}
