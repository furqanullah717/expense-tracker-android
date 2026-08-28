package com.smartspend.ai.feature.home

import androidx.lifecycle.viewModelScope
import com.smartspend.ai.base.BaseViewModel
import com.smartspend.ai.base.HomeNavigationEvent
import com.smartspend.ai.base.UiEvent
import com.smartspend.ai.utils.FakeDataGenerator
import com.smartspend.ai.data.dao.ExpenseDao
import com.smartspend.ai.data.model.ExpenseEntity
import com.smartspend.ai.utils.Utils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(val dao: ExpenseDao) : BaseViewModel() {
    val expenses = dao.getAllExpense()

    companion object {
        const val INITIAL_SEED_MONEY = 100_000_000.0 // Ï¥àÍ∏∞ ?úÎìúÎ®∏Îãà 1????
    }

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

    // Ï¥??îÏï° (?úÎìúÎ®∏Îãà 2??+ ?úÏàò??
    fun getTotalBalance(list: List<ExpenseEntity>): String {
        val net = getNetProfitAmount(list)
        return Utils.formatCurrency(INITIAL_SEED_MONEY + net)
    }

    // ?úÏàò??(?òÏûÖ - ÏßÄÏ∂??ÑÏ†Å)
    fun getNetProfit(list: List<ExpenseEntity>): String {
        val net = getNetProfitAmount(list)
        val prefix = if (net > 0) "+" else ""
        return prefix + Utils.formatCurrency(net)
    }

    fun getNetProfitAmount(list: List<ExpenseEntity>): Double {
        var net = 0.0
        for (expense in list) {
            if (expense.type == "Income") {
                net += expense.amount
            } else {
                net -= expense.amount
            }
        }
        return net
    }

    fun getBalance(list: List<ExpenseEntity>): String {
        return getTotalBalance(list)
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
