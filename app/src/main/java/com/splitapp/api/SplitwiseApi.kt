package com.splitapp.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.*

// --- Response models ---

data class SplitwiseUser(
    val id: Long,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String?,
    val email: String?
)

data class CurrentUserResponse(
    @SerializedName("user") val user: SplitwiseUser?  // Splitwise returns { "user": { ... } }
)

data class FriendsResponse(
    val friends: List<SplitwiseUser>
)

data class SplitwiseGroup(
    val id: Long,
    val name: String,
    val members: List<SplitwiseUser>
)

data class GroupsResponse(
    val groups: List<SplitwiseGroup>
)

data class ExpenseResponse(
    val expenses: List<Any>?,
    val errors: Map<String, List<String>>?
)

// --- Retrofit interface ---

interface SplitwiseApi {

    @GET("api/v3.0/get_current_user")
    suspend fun getCurrentUser(
        @Header("Authorization") bearerToken: String
    ): CurrentUserResponse

    @GET("api/v3.0/get_friends")
    suspend fun getFriends(
        @Header("Authorization") bearerToken: String
    ): FriendsResponse

    @GET("api/v3.0/get_groups")
    suspend fun getGroups(
        @Header("Authorization") bearerToken: String
    ): GroupsResponse

    @POST("api/v3.0/create_expense")
    @FormUrlEncoded
    suspend fun createExpense(
        @Header("Authorization") bearerToken: String,
        @FieldMap fields: Map<String, String>
    ): ExpenseResponse
}
