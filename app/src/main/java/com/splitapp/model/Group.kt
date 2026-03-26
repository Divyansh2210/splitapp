package com.splitapp.model

data class Group(
    val id: Long,
    val name: String,
    val members: List<Friend>   // members of the group (excluding current user)
)
