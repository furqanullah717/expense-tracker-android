package com.codewithfk.expensetracker.android.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.codewithfk.expensetracker.android.data.ai.firebase.FirebaseAiGateway

@Entity(tableName = "ai_analysis_history")
data class AiAnalysisEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String = "",
    val firestoreId: String = "",
    val title: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val transactionCount: Int = 0,
    val totalExpenseSum: Double = 0.0,
    val totalIncomeSum: Double = 0.0,
    val geminiPrompt: String = "",
    val rawResponseJson: String = "",
    val responseTimeMs: Long = 0L,
    val promptTokens: Int = 0,
    val candidatesTokens: Int = 0,
    val totalTokens: Int = 0,
    val estimatedCostUsd: Double = 0.0,
    val estimatedCostKrw: Double = 0.0,
    val modelName: String = FirebaseAiGateway.modelName,
    val agentVersion: String = FirebaseAiGateway.agentVersion,
    val provider: String = FirebaseAiGateway.provider,
    val authMethod: String = "Firebase App Check (Debug/Integrity)",
    val appCheckStatus: String = "Verified",
    val networkType: String = "Unknown",
    val deviceModel: String = "",
    val osVersion: String = "",
    val promptCharLength: Int = 0,
    val responseCharLength: Int = 0,
    val finishReason: String = "STOP",
    val summary: String = "",
    val insights: String = "",
    val savingTips: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
