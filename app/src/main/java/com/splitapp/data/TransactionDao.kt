package com.splitapp.data

import androidx.room.*
import com.splitapp.model.Transaction
import com.splitapp.model.TransactionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAll(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): Transaction?

    @Query("SELECT * FROM transactions WHERE status = 'PENDING' ORDER BY timestamp DESC")
    fun getPending(): Flow<List<Transaction>>

    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Query("UPDATE transactions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: TransactionStatus)
}
