package com.splitapp.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TransactionStatus { PENDING, DONE, SKIPPED }

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val description: String,
    val rawSms: String,
    val timestamp: Long,
    val status: TransactionStatus = TransactionStatus.PENDING
)
