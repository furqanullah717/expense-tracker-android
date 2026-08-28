package com.smartspend.ai.base

sealed class NavigationEvent {
    object NavigateBack : NavigationEvent()
}

sealed class AddExpenseNavigationEvent : NavigationEvent() {
    object MenuOpenedClicked : AddExpenseNavigationEvent()
}

sealed class HomeNavigationEvent : NavigationEvent() {
    object NavigateToAddExpense : HomeNavigationEvent()
    object NavigateToAddIncome : HomeNavigationEvent()
    object NavigateToSeeAll : HomeNavigationEvent()
}