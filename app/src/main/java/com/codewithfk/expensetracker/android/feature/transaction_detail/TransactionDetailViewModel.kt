package com.codewithfk.expensetracker.android.feature.transaction_detail

import androidx.lifecycle.viewModelScope
import com.codewithfk.expensetracker.android.base.BaseViewModel
import com.codewithfk.expensetracker.android.base.NavigationEvent
import com.codewithfk.expensetracker.android.base.UiEvent
import com.codewithfk.expensetracker.android.data.dao.ExpenseDao
import com.codewithfk.expensetracker.android.data.model.ExpenseEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(val dao: ExpenseDao) : BaseViewModel() {

    private val _transaction = MutableStateFlow<ExpenseEntity?>(null)
    val transaction: StateFlow<ExpenseEntity?> = _transaction

    fun loadTransaction(id: Int) {
        viewModelScope.launch {
            _transaction.value = dao.getExpenseById(id)
        }
    }

    override fun onEvent(event: UiEvent) {
        when (event) {
            is TransactionDetailUiEvent.OnDeleteClicked -> {
                viewModelScope.launch {
                    dao.deleteExpense(event.transaction)
                    _navigationEvent.emit(NavigationEvent.NavigateBack)
                }
            }
            is TransactionDetailUiEvent.OnBackPressed -> {
                viewModelScope.launch {
                    _navigationEvent.emit(NavigationEvent.NavigateBack)
                }
            }
        }
    }
}

sealed class TransactionDetailUiEvent : UiEvent() {
    data class OnDeleteClicked(val transaction: ExpenseEntity) : TransactionDetailUiEvent()
    object OnBackPressed : TransactionDetailUiEvent()
}
