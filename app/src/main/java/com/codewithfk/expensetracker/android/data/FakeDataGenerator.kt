package com.codewithfk.expensetracker.android.data

import com.codewithfk.expensetracker.android.data.dao.ExpenseDao
import com.codewithfk.expensetracker.android.data.model.ExpenseEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

object FakeDataGenerator {

    suspend fun generateFakeData(dao: ExpenseDao) {
        dao.deleteAllExpenses()

        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        val incomes = listOf("월급", "프리랜서", "보너스", "임대 수입")
        val expenses = listOf(
            "식비", "넷플릭스", "월세", "카페/스타벅스", "쇼핑", 
            "교통비", "공과금", "외식", "의료/건강", "여행"
        )

        // Go back 2 years
        calendar.add(Calendar.YEAR, -2)

        val endCalendar = Calendar.getInstance()

        while (calendar.before(endCalendar)) {
            // Monthly Salary
            if (calendar.get(Calendar.DAY_OF_MONTH) == 1) {
                dao.insertExpense(
                    ExpenseEntity(
                        null,
                        "월급",
                        5000000.0 + Random.nextDouble(-500000.0, 500000.0),
                        dateFormat.format(calendar.time),
                        "Income"
                    )
                )
                
                // Rent
                dao.insertExpense(
                    ExpenseEntity(
                        null,
                        "월세",
                        1200000.0,
                        dateFormat.format(calendar.time),
                        "Expense"
                    )
                )
            }

            // Random incomes
            if (Random.nextInt(30) == 0) { // roughly once a month
                dao.insertExpense(
                    ExpenseEntity(
                        null,
                        incomes.random(),
                        Random.nextDouble(100000.0, 1000000.0),
                        dateFormat.format(calendar.time),
                        "Income"
                    )
                )
            }

            // Daily/Frequent expenses
            val numExpenses = Random.nextInt(0, 3)
            repeat(numExpenses) {
                dao.insertExpense(
                    ExpenseEntity(
                        null,
                        expenses.random(),
                        Random.nextDouble(5000.0, 100000.0),
                        dateFormat.format(calendar.time),
                        "Expense"
                    )
                )
            }

            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
    }
}
