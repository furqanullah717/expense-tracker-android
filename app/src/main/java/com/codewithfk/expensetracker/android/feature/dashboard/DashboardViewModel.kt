package com.codewithfk.expensetracker.android.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codewithfk.expensetracker.android.data.dao.ExpenseDao
import com.codewithfk.expensetracker.android.data.model.ExpenseSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class MonthlyStats(
    val totalIncome: Double,
    val totalExpenses: Double,
    val savings: Double
)

data class CategorySummary(
    val category: String,
    val amount: Double,
    val percentage: Float
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val expenseDao: ExpenseDao
) : ViewModel() {

    private val _monthlyStats = MutableStateFlow<MonthlyStats?>(null)
    val monthlyStats: StateFlow<MonthlyStats?> = _monthlyStats.asStateFlow()

    private val _categoryBreakdown = MutableStateFlow<List<CategorySummary>>(emptyList())
    val categoryBreakdown: StateFlow<List<CategorySummary>> = _categoryBreakdown.asStateFlow()

    val expenseSummary = expenseDao.getAllExpense()
        .map { expenses ->
            expenses.groupBy { it.date }
                .map { (date, expensesForDate) ->
                    ExpenseSummary(
                        type = if (expensesForDate.any { it.type == "Income" }) "Income" else "Expense",
                        date = date,
                        total_amount = expensesForDate.sumOf { it.amount }
                    )
                }
                .sortedByDescending { it.date }
        }

    init {
        calculateMonthlyStats()
        calculateCategoryBreakdown()
    }

    private fun calculateMonthlyStats() {
        viewModelScope.launch {
            expenseDao.getAllExpense()
                .collect { expenses ->
                    val currentMonth = SimpleDateFormat("MM/yyyy", Locale.getDefault())
                        .format(Date())

                    val monthlyExpenses = expenses.filter { expense ->
                        expense.date.endsWith(currentMonth)
                    }

                    val totalIncome = monthlyExpenses
                        .filter { it.type == "Income" }
                        .sumOf { it.amount }

                    val totalExpenses = monthlyExpenses
                        .filter { it.type == "Expense" }
                        .sumOf { it.amount }

                    _monthlyStats.value = MonthlyStats(
                        totalIncome = totalIncome,
                        totalExpenses = totalExpenses,
                        savings = totalIncome - totalExpenses
                    )
                }
        }
    }

    private fun calculateCategoryBreakdown() {
        viewModelScope.launch {
            expenseDao.getAllExpense()
                .collect { expenses ->
                    val expensesByCategory = expenses
                        .filter { it.type == "Expense" }
                        .groupBy { it.title }
                        .map { (category, expensesInCategory) ->
                            category to expensesInCategory.sumOf { it.amount }
                        }

                    val totalExpenses = expensesByCategory.sumOf { it.second }

                    _categoryBreakdown.value = expensesByCategory.map { (category, amount) ->
                        CategorySummary(
                            category = category,
                            amount = amount,
                            percentage = (amount / totalExpenses).toFloat()
                        )
                    }.sortedByDescending { it.amount }
                }
        }
    }
}