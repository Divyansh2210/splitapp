package com.splitapp

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.splitapp.data.AppDatabase

class SplitApplication : Application() {

    companion object {
        @Volatile private var db: AppDatabase? = null

        fun database(context: Context): AppDatabase {
            return db ?: synchronized(this) {
                db ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "splitapp.db"
                ).build().also { db = it }
            }
        }
    }
}
