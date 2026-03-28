package com.splitapp.api

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for Splitwise API response model deserialization.
 * Verifies @SerializedName annotations work correctly.
 */
class SplitwiseApiModelsTest {

    private val gson = Gson()

    // =========================================================================
    // CurrentUserResponse
    // =========================================================================

    @Test
    fun `CurrentUserResponse deserializes user field`() {
        val json = """{"user": {"id": 123, "first_name": "John", "last_name": "Doe", "email": "john@test.com"}}"""
        val response = gson.fromJson(json, CurrentUserResponse::class.java)
        assertNotNull(response.user)
        assertEquals(123L, response.user!!.id)
        assertEquals("John", response.user!!.firstName)
        assertEquals("Doe", response.user!!.lastName)
        assertEquals("john@test.com", response.user!!.email)
    }

    @Test
    fun `CurrentUserResponse with null user`() {
        val json = """{"user": null}"""
        val response = gson.fromJson(json, CurrentUserResponse::class.java)
        assertNull(response.user)
    }

    // =========================================================================
    // FriendsResponse
    // =========================================================================

    @Test
    fun `FriendsResponse deserializes friend list`() {
        val json = """{"friends": [
            {"id": 1, "first_name": "Alice", "last_name": "A", "email": "alice@a.com"},
            {"id": 2, "first_name": "Bob", "last_name": null, "email": null}
        ]}"""
        val response = gson.fromJson(json, FriendsResponse::class.java)
        assertEquals(2, response.friends.size)
        assertEquals("Alice", response.friends[0].firstName)
        assertNull(response.friends[1].lastName)
    }

    @Test
    fun `FriendsResponse empty list`() {
        val json = """{"friends": []}"""
        val response = gson.fromJson(json, FriendsResponse::class.java)
        assertTrue(response.friends.isEmpty())
    }

    // =========================================================================
    // GroupsResponse
    // =========================================================================

    @Test
    fun `GroupsResponse deserializes groups with members`() {
        val json = """{"groups": [
            {"id": 10, "name": "Flatmates", "members": [
                {"id": 1, "first_name": "Alice", "last_name": "A", "email": "a@a.com"},
                {"id": 2, "first_name": "Bob", "last_name": "B", "email": null}
            ]},
            {"id": 0, "name": "Non-group expenses", "members": []}
        ]}"""
        val response = gson.fromJson(json, GroupsResponse::class.java)
        assertEquals(2, response.groups.size)
        assertEquals("Flatmates", response.groups[0].name)
        assertEquals(2, response.groups[0].members.size)
    }

    // =========================================================================
    // ExpenseResponse
    // =========================================================================

    @Test
    fun `ExpenseResponse with no errors`() {
        val json = """{"expenses": [{}], "errors": null}"""
        val response = gson.fromJson(json, ExpenseResponse::class.java)
        assertNull(response.errors)
    }

    @Test
    fun `ExpenseResponse with errors`() {
        val json = """{"expenses": null, "errors": {"base": ["Invalid API key", "Amount must be positive"]}}"""
        val response = gson.fromJson(json, ExpenseResponse::class.java)
        assertNotNull(response.errors)
        val errors = response.errors!!["base"]!!
        assertEquals(2, errors.size)
        assertTrue(errors.contains("Invalid API key"))
    }

    @Test
    fun `ExpenseResponse errors can be flattened into message`() {
        val json = """{"expenses": null, "errors": {"base": ["Error 1"], "amount": ["Error 2"]}}"""
        val response = gson.fromJson(json, ExpenseResponse::class.java)
        val message = response.errors!!.values.flatten().joinToString(", ")
        assertTrue(message.contains("Error 1"))
        assertTrue(message.contains("Error 2"))
    }

    // =========================================================================
    // SplitwiseUser snake_case deserialization
    // =========================================================================

    @Test
    fun `SplitwiseUser first_name maps to firstName`() {
        val json = """{"id": 1, "first_name": "Test", "last_name": "User", "email": "t@u.com"}"""
        val user = gson.fromJson(json, SplitwiseUser::class.java)
        assertEquals("Test", user.firstName)
        assertEquals("User", user.lastName)
    }
}
