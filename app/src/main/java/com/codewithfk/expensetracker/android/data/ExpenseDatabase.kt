package com.codewithfk.expensetracker.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.codewithfk.expensetracker.android.data.dao.ExpenseDao
import com.codewithfk.expensetracker.android.data.model.ExpenseEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [ExpenseEntity::class], version = 1, exportSchema = false)
abstract class ExpenseDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao

    companion object {
        const val DATABASE_NAME = "expense_database"

        @JvmStatic
        fun getInstance(context: Context): ExpenseDatabase {
            return Room.databaseBuilder(
                context, ExpenseDatabase::class.java,
                DATABASE_NAME
            ).addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
//                    InitBasicData(context)
                }

                fun InitBasicData(context: Context) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val dao = getInstance(context).expenseDao()
                        dao.insertExpense(
                            ExpenseEntity(
                                null,
                                "Salary",
                                5000.40,
                                "2021-08-01",
                                "Salary",
                                "Income"
                            )
                        )
                        dao.insertExpense(
                            ExpenseEntity(
                                null,
                                "Paypal",
                                2000.50,
                                "2021-08-01",
                                "Paypal",
                                "Income"
                            )
                        )
                        dao.insertExpense(
                            ExpenseEntity(
                                null,
                                "Netflix",
                                100.43,
                                "2021-08-01",
                                "Netflix",
                                "Expense"
                            )
                        )
                        dao.insertExpense(
                            ExpenseEntity(
                                null,
                                "Starbucks",
                                400.56,
                                "2021-08-02",
                                "Starbucks",
                                "Income"
                            )
                        )
                    }
                }
            })
                .build()
        }
    }
}