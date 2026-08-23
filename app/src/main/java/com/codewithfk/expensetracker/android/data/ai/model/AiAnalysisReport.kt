package com.codewithfk.expensetracker.android.data.ai.model

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(InternalSerializationApi::class)
@Serializable
data class AiAnalysisReport(
    // Standard fields (used by getAnalyzeSpendingPrompt)
    val summary: String? = null,
    val insights: List<String>? = null,
    val savingTips: List<String>? = null,
    val period: String,

    // Detailed fields (used by getAnalyzeDetailSpendingPrompt)
    val executiveSummary: String? = null,
    val financialHealthScore: Int? = null,
    val keyInsights: List<KeyInsight>? = null,
    val actionableStrategies: List<ActionableStrategy>? = null
)

@OptIn(InternalSerializationApi::class)
@Serializable
data class KeyInsight(
    val topic: String,
    val finding: String,
    val implication: String
)

@OptIn(InternalSerializationApi::class)
@Serializable
data class ActionableStrategy(
    val urgency: String,
    val advice: String,
    val expectedImpact: String
)
