package com.splitapp.model

import java.util.UUID

data class OrderItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val price: Double,
    val isFee: Boolean = false
)
