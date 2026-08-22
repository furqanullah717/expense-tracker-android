package com.codewithfk.expensetracker.android.data.ai.model

data class AiAnalysisReport(
    val summary: String,
    val insights: List<String>,
    val savingTips: List<String>,
    val period: String
)
