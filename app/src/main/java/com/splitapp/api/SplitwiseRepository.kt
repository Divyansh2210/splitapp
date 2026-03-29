package com.splitapp.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.splitapp.BuildConfig
import com.splitapp.data.PreferencesStore
import com.splitapp.model.Friend
import com.splitapp.model.Group
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SplitwiseRepository(private val prefs: PreferencesStore) {

    private val gson = Gson()

    private val api: SplitwiseApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        Retrofit.Builder()
            .baseUrl("https://secure.splitwise.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SplitwiseApi::class.java)
    }

    private suspend fun bearerToken(): String = "Bearer ${prefs.apiKey.first()}"

    suspend fun getCurrentUserId(): Result<Long> = runCatching {
        val response = api.getCurrentUser(bearerToken())
        val user = response.user
            ?: error("Splitwise returned no user — check your API key in Settings")
        prefs.setCurrentUserId(user.id.toString())
        user.id
    }

    suspend fun syncFriends(): Result<List<Friend>> = runCatching {
        val response = api.getFriends(bearerToken())
        val friends = response.friends.map { user ->
            Friend(
                id = user.id,
                firstName = user.firstName,
                lastName = user.lastName,
                email = user.email
            )
        }
        prefs.setCachedFriends(gson.toJson(friends))
        friends
    }

    suspend fun getCachedFriends(): List<Friend> {
        val json = prefs.cachedFriendsJson.first()
        if (json.isBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<Friend>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun syncGroups(): Result<List<Group>> = runCatching {
        val currentUserIdStr = prefs.currentUserId.first()
        val response = api.getGroups(bearerToken())
        // Skip the special "Non-group expenses" group (id=0) and filter out current user from members
        val groups = response.groups
            .filter { it.id != 0L }
            .map { g ->
                Group(
                    id = g.id,
                    name = g.name,
                    members = g.members
                        .filter { it.id.toString() != currentUserIdStr }
                        .map { user ->
                            Friend(
                                id = user.id,
                                firstName = user.firstName,
                                lastName = user.lastName,
                                email = user.email
                            )
                        }
                )
            }
        prefs.setCachedGroups(gson.toJson(groups))
        groups
    }

    suspend fun getCachedGroups(): List<Group> {
        val json = prefs.cachedGroupsJson.first()
        if (json.isBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<Group>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Creates an expense on Splitwise.
     *
     * @param totalAmount   Total cost (you paid everything)
     * @param description   Expense description
     * @param currentUserId Your Splitwise user ID
     * @param splits        List of (friendId, owedAmount)
     * @param groupId       Optional group to associate the expense with
     */
    suspend fun createExpense(
        totalAmount: Double,
        description: String,
        currentUserId: Long,
        splits: List<Pair<Long, Double>>,
        groupId: Long? = null
    ): Result<Unit> = runCatching {
        val fields = mutableMapOf<String, String>()
        fields["cost"] = "%.2f".format(totalAmount)
        fields["description"] = description
        fields["split_equally"] = "false"
        groupId?.let { fields["group_id"] = it.toString() }

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

        val response = api.createExpense(bearerToken(), fields)
        val errors = response.errors
        if (!errors.isNullOrEmpty()) {
            val message = errors.values.flatten().joinToString(", ")
            error("Splitwise error: $message")
        }
    }
}
