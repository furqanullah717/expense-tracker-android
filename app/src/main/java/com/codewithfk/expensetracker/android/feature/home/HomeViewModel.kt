package com.codewithfk.expensetracker.android.feature.home

import androidx.lifecycle.viewModelScope
import com.codewithfk.expensetracker.android.base.BaseViewModel
import com.codewithfk.expensetracker.android.base.HomeNavigationEvent
import com.codewithfk.expensetracker.android.base.UiEvent
import com.codewithfk.expensetracker.android.utils.Utils
import com.codewithfk.expensetracker.android.data.FakeDataGenerator
import com.codewithfk.expensetracker.android.data.dao.ExpenseDao
import com.codewithfk.expensetracker.android.data.model.ExpenseEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(val dao: ExpenseDao) : BaseViewModel() {
    val expenses = dao.getAllExpense()

    override fun onEvent(event: UiEvent) {
        when (event) {
            is HomeUiEvent.OnAddExpenseClicked -> {
                viewModelScope.launch {
                    _navigationEvent.emit(HomeNavigationEvent.NavigateToAddExpense)
                }
            }

            is HomeUiEvent.OnAddIncomeClicked -> {
                viewModelScope.launch {
                    _navigationEvent.emit(HomeNavigationEvent.NavigateToAddIncome)
                }
            }

            is HomeUiEvent.OnSeeAllClicked -> {
                viewModelScope.launch {
                    _navigationEvent.emit(HomeNavigationEvent.NavigateToSeeAll)
                }
            }

            is HomeUiEvent.OnSeedDataClicked -> {
                viewModelScope.launch {
                    FakeDataGenerator.generateFakeData(dao)
                }
            }
        }
    }

    fun getBalance(list: List<ExpenseEntity>): String {
        var balance = 0.0
        for (expense in list) {
            if (expense.type == "Income") {
                balance += expense.amount
            } else {
                balance -= expense.amount
            }
        }
        return Utils.formatCurrency(balance)
    }

    fun getTotalExpense(list: List<ExpenseEntity>): String {
        var total = 0.0
        for (expense in list) {
            if (expense.type != "Income") {
                total += expense.amount
            }
        }

        return Utils.formatCurrency(total)
    }

    fun getTotalIncome(list: List<ExpenseEntity>): String {
        var totalIncome = 0.0
        for (expense in list) {
            if (expense.type == "Income") {
                totalIncome += expense.amount
            }
        }
        return Utils.formatCurrency(totalIncome)
    }

    fun getThisMonthIncome(list: List<ExpenseEntity>): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -1)
        val thisMonth = calendar.get(Calendar.MONTH)
        val year = calendar.get(Calendar.YEAR)

        var total = 0.0
        for (expense in list) {
            if (expense.type == "Income") {
                val expenseDate = Calendar.getInstance()
                expenseDate.timeInMillis = Utils.getMillisFromDate(expense.date)
                if (expenseDate.get(Calendar.MONTH) == thisMonth && expenseDate.get(Calendar.YEAR) == year) {
                    total += expense.amount
                }
            }
        }
        return Utils.formatCurrency(total)
    }
    fun getLastMonthIncome(list: List<ExpenseEntity>): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -1)
        val lastMonth = calendar.get(Calendar.MONTH)
        val year = calendar.get(Calendar.YEAR)

        var total = 0.0
        for (expense in list) {
            if (expense.type == "Income") {
                val expenseDate = Calendar.getInstance()
                expenseDate.timeInMillis = Utils.getMillisFromDate(expense.date)
                if (expenseDate.get(Calendar.MONTH) == lastMonth && expenseDate.get(Calendar.YEAR) == year) {
                    total += expense.amount
                }
            }
        }
        return Utils.formatCurrency(total)
    }

    fun getThisMonthExpense(list: List<ExpenseEntity>): String {
        val calendar = Calendar.getInstance()
        val thisMonth = calendar.get(Calendar.MONTH)
        val year = calendar.get(Calendar.YEAR)

        var total = 0.0
        for (expense in list) {
            if (expense.type == "Expense") {
                val expenseDate = Calendar.getInstance()
                expenseDate.timeInMillis = Utils.getMillisFromDate(expense.date)
                if (expenseDate.get(Calendar.MONTH) == thisMonth && expenseDate.get(Calendar.YEAR) == year) {
                    total += expense.amount
                }
            }
        }
        return Utils.formatCurrency(total)
    }
}

sealed class HomeUiEvent : UiEvent() {
    data object OnAddExpenseClicked : HomeUiEvent()
    data object OnAddIncomeClicked : HomeUiEvent()
    data object OnSeeAllClicked : HomeUiEvent()
    data object OnSeedDataClicked : HomeUiEvent()
}
