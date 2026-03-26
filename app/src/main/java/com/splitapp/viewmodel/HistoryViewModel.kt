package com.splitapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.splitapp.SplitApplication
import com.splitapp.model.Transaction
import kotlinx.coroutines.flow.Flow

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SplitApplication.database(application)

    val transactions: Flow<List<Transaction>> = db.transactionDao().getAll()
}
