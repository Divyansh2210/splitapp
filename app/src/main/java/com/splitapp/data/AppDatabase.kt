package com.splitapp.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.splitapp.model.Transaction

@Database(entities = [Transaction::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}
