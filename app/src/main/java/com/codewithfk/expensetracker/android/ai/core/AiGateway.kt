package com.codewithfk.expensetracker.android.ai.core

import com.codewithfk.expensetracker.android.ai.core.model.AiAnalysisReport
import com.codewithfk.expensetracker.android.ai.core.model.AiMasterRouterResponse
import com.codewithfk.expensetracker.android.data.model.ChatMessageEntity
import com.codewithfk.expensetracker.android.data.model.ExpenseEntity
import kotlinx.coroutines.flow.Flow

data class AiAnalysisResultData(
    val report: AiAnalysisReport,
    val geminiPrompt: String,
    val responseTimeMs: Long,
    val promptTokens: Int? = null,
    val candidatesTokens: Int? = null,
    val totalTokens: Int? = null,
    val modelName: String = FirebaseAiGateway.modelName,
    val agentVersion: String = FirebaseAiGateway.agentVersion,
    val provider: String = FirebaseAiGateway.provider,
    val rawResponseJson: String = ""
)

data class AiMasterRouterResultData(
    val response: AiMasterRouterResponse,
    val responseTimeMs: Long,
    val promptTokens: Int? = null,
    val candidatesTokens: Int? = null,
    val totalTokens: Int? = null,
    val modelName: String = FirebaseAiGateway.smartModelName
)

data class AiImageAttachment(
    val bytes: ByteArray,
    val mimeType: String
)

typealias AiFileAttachment = AiImageAttachment

/**
 * Interface defining the contract for AI-powered features in the Expense Tracker.
 * This will be implemented by both Firebase (SaaS) and Supabase (BYOK) providers.
 */
interface AiGateway {
    /**
     * Parses a natural language input string into a structured ExpenseEntity.
     * Example: "오늘 식비로 15000원 썼어" -> ExpenseEntity(title="식비", amount=15000.0, ...)
     */
    suspend fun parseExpense(input: String, isIncome: Boolean): Result<ExpenseEntity>

    /**
     * Determines the user's intent from natural language input.
     * This is the 'Master Router' that classifies input into categories like DATA_RETRIEVAL, DATA_ANALYSIS, etc.
     */
    suspend fun routeIntent(input: String, modelName: String? = null): Result<AiMasterRouterResultData>

    /**
     * Analyzes a list of expenses to provide insights and saving tips.
     * This method handles large datasets (e.g., 2 years of history).
     */
    suspend fun analyzeSpending(
        history: List<ExpenseEntity>,
        startDate: String? = null,
        endDate: String? = null
    ): Result<AiAnalysisResultData>

    /**
     * General chat function to answer user questions using streaming.
     */
    fun chatStream(
        messages: List<ChatMessageEntity>,
        history: List<ExpenseEntity>,
        modelName: String? = null,
        historyLimit: Int = 20,
        images: List<AiImageAttachment> = emptyList(),
        files: List<AiFileAttachment> = emptyList()
    ): Flow<ChatResponse>
}

sealed class ChatResponse {
    data class Chunk(val text: String) : ChatResponse()
    data class Metadata(
        val promptTokens: Int?,
        val candidatesTokens: Int?,
        val totalTokens: Int?,
        val responseTimeMs: Long,
        val modelName: String,
        val agentVersion: String,
        val provider: String
    ) : ChatResponse()
}
