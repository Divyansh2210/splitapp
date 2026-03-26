package com.splitapp.model

data class Friend(
    val id: Long,
    val firstName: String,
    val lastName: String?,
    val email: String?
) {
    val displayName: String get() = listOfNotNull(firstName, lastName).joinToString(" ")
}
