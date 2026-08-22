package com.codewithfk.expensetracker.android.data.ai

import com.codewithfk.expensetracker.android.data.ai.model.AiAnalysisReport
import com.codewithfk.expensetracker.android.data.model.ExpenseEntity

/**
 * Interface defining the contract for AI-powered features in the Expense Tracker.
 * This will be implemented by both Firebase (SaaS) and Supabase (BYOK) providers.
 */
interface AiGateway {
    
    /**
     * Parses a natural language input string into a structured ExpenseEntity.
     * Example: "오늘 식비로 15000원 썼어" -> ExpenseEntity(title="식비", amount=15000.0, ...)
     */
    suspend fun parseExpense(input: String): Result<ExpenseEntity>

    /**
     * Analyzes a list of expenses to provide insights and saving tips.
     * This method handles large datasets (e.g., 2 years of history).
     */
    suspend fun analyzeSpending(history: List<ExpenseEntity>): Result<AiAnalysisReport>
}
