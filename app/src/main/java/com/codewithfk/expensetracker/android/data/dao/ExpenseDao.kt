package com.codewithfk.expensetracker.android.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.codewithfk.expensetracker.android.data.model.ExpenseEntity
import com.codewithfk.expensetracker.android.data.model.ExpenseSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {


    @Query("SELECT * FROM expense_table")
    fun getAllExpense(): Flow<List<ExpenseEntity>>
    @Query("SELECT type, date, SUM(amount) AS total_amount FROM expense_table GROUP BY type, date ORDER BY date")
    fun getAllExpenseByDate(): Flow<List<ExpenseSummary>>

    @Insert
    suspend fun insertExpense(expenseEntity: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expenseEntity: ExpenseEntity)

    @Update
    suspend fun updateExpense(expenseEntity: ExpenseEntity)
}