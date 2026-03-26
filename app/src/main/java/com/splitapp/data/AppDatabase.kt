package com.splitapp.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.splitapp.model.Transaction
import com.splitapp.model.TransactionStatus

class Converters {
    @TypeConverter
    fun fromStatus(status: TransactionStatus): String = status.name

    @TypeConverter
    fun toStatus(name: String): TransactionStatus = TransactionStatus.valueOf(name)
}

@Database(entities = [Transaction::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}
