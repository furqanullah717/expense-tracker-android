package com.codewithfk.expensetracker.android.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.codewithfk.expensetracker.android.data.ExpenseDatabase
import com.codewithfk.expensetracker.android.data.dao.ExpenseDao
import com.codewithfk.expensetracker.android.data.model.ExpenseEntity
import java.lang.IllegalArgumentException

class AddExpenseViewModel(val dao: ExpenseDao) : ViewModel() {

    suspend fun addExpense(expenseEntity: ExpenseEntity): Boolean {
        return try {
            dao.insertExpense(expenseEntity)
            true
        } catch (ex: Throwable) {
            false
        }
    }
}

class AddExpenseViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddExpenseViewModel::class.java)) {
            val dao = ExpenseDatabase.getInstance(context).expenseDao()
            return AddExpenseViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

