package com.splitapp.api

import com.splitapp.model.Friend
import com.splitapp.model.Group
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for Splitwise group/friend sync processing logic.
 *
 * Verifies:
 * - Group id=0 ("Non-group expenses") is filtered out
 * - Current user is excluded from group member lists
 * - SplitwiseUser -> Friend mapping is correct
 * - Cached JSON round-trips correctly
 */
class SplitwiseGroupFilterTest {

    private val currentUserId = "99"

    // =========================================================================
    // Group filtering
    // =========================================================================

    @Test
    fun `group id 0 is filtered out`() {
        val apiGroups = listOf(
            SplitwiseGroup(0, "Non-group expenses", emptyList()),
            SplitwiseGroup(10, "Flatmates", listOf(
                SplitwiseUser(1, "Alice", "A", "a@a.com")
            ))
        )
        val filtered = apiGroups.filter { it.id != 0L }
        assertEquals(1, filtered.size)
        assertEquals("Flatmates", filtered[0].name)
    }

    @Test
    fun `current user excluded from group members`() {
        val apiGroup = SplitwiseGroup(10, "Flat", listOf(
            SplitwiseUser(99, "Me", "Myself", "me@me.com"),
            SplitwiseUser(1, "Alice", "A", "a@a.com"),
            SplitwiseUser(2, "Bob", "B", null)
        ))
        val members = apiGroup.members
            .filter { it.id.toString() != currentUserId }
            .map { Friend(it.id, it.firstName, it.lastName, it.email) }

        assertEquals(2, members.size)
        assertFalse(members.any { it.id == 99L })
        assertTrue(members.any { it.id == 1L })
        assertTrue(members.any { it.id == 2L })
    }

    @Test
    fun `group with only current user has empty members after filter`() {
        val apiGroup = SplitwiseGroup(10, "Solo", listOf(
            SplitwiseUser(99, "Me", null, null)
        ))
        val members = apiGroup.members
            .filter { it.id.toString() != currentUserId }
            .map { Friend(it.id, it.firstName, it.lastName, it.email) }

        assertTrue(members.isEmpty())
    }

    // =========================================================================
    // Friend mapping
    // =========================================================================

    @Test
    fun `SplitwiseUser maps to Friend correctly`() {
        val user = SplitwiseUser(42, "Alice", "Wonder", "alice@test.com")
        val friend = Friend(user.id, user.firstName, user.lastName, user.email)

        assertEquals(42L, friend.id)
        assertEquals("Alice", friend.firstName)
        assertEquals("Wonder", friend.lastName)
        assertEquals("alice@test.com", friend.email)
        assertEquals("Alice Wonder", friend.displayName)
    }

    @Test
    fun `SplitwiseUser with null fields maps correctly`() {
        val user = SplitwiseUser(1, "Solo", null, null)
        val friend = Friend(user.id, user.firstName, user.lastName, user.email)

        assertEquals("Solo", friend.displayName)
        assertNull(friend.email)
    }

    // =========================================================================
    // Cached JSON round-trip
    // =========================================================================

    @Test
    fun `friends JSON round-trip`() {
        val gson = com.google.gson.Gson()
        val friends = listOf(
            Friend(1, "Alice", "A", "a@a.com"),
            Friend(2, "Bob", null, null)
        )
        val json = gson.toJson(friends)
        val type = object : com.google.gson.reflect.TypeToken<List<Friend>>() {}.type
        val restored: List<Friend> = gson.fromJson(json, type)

        assertEquals(2, restored.size)
        assertEquals("Alice", restored[0].firstName)
        assertEquals("A", restored[0].lastName)
        assertNull(restored[1].lastName)
    }

    @Test
    fun `groups JSON round-trip`() {
        val gson = com.google.gson.Gson()
        val groups = listOf(
            Group(10, "Flat", listOf(Friend(1, "Alice", "A", null))),
            Group(20, "Office", emptyList())
        )
        val json = gson.toJson(groups)
        val type = object : com.google.gson.reflect.TypeToken<List<Group>>() {}.type
        val restored: List<Group> = gson.fromJson(json, type)

        assertEquals(2, restored.size)
        assertEquals(1, restored[0].members.size)
        assertEquals("Alice", restored[0].members[0].firstName)
        assertTrue(restored[1].members.isEmpty())
    }

    @Test
    fun `empty JSON returns empty list`() {
        val gson = com.google.gson.Gson()
        val json = ""
        // This mirrors getCachedFriends/getCachedGroups behavior
        val result: List<Friend> = if (json.isBlank()) emptyList()
        else {
            val type = object : com.google.gson.reflect.TypeToken<List<Friend>>() {}.type
            gson.fromJson(json, type)
        }
        assertTrue(result.isEmpty())
    }

    @Test
    fun `malformed JSON returns empty list`() {
        val gson = com.google.gson.Gson()
        val json = "not valid json"
        val result: List<Friend> = try {
            val type = object : com.google.gson.reflect.TypeToken<List<Friend>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
        assertTrue(result.isEmpty())
    }
}
