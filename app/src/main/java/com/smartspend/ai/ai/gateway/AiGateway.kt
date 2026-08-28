package com.smartspend.ai.ai.gateway

import com.smartspend.ai.ai.model.AiAnalysisReport
import com.smartspend.ai.ai.model.AiMasterRouterResponse
import com.smartspend.ai.data.model.ChatMessageEntity
import com.smartspend.ai.data.model.ExpenseEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

data class AiAnalysisResultData(
    val report: AiAnalysisReport,
    val geminiPrompt: String,
    val responseTimeMs: Long,
    val promptTokens: Int? = null,
    val candidatesTokens: Int? = null,
    val totalTokens: Int? = null,
    val modelName: String = AiModelCatalog.modelName,
    val agentVersion: String = AiModelCatalog.agentVersion,
    val provider: String = AiModelCatalog.provider,
    val rawResponseJson: String = "",
    val thoughtsTokens: Int? = null
)

data class AiMasterRouterResultData(
    val response: AiMasterRouterResponse,
    val responseTimeMs: Long,
    val promptTokens: Int? = null,
    val candidatesTokens: Int? = null,
    val totalTokens: Int? = null,
    val modelName: String = AiModelCatalog.smartModelName,
    val prompt: String = "",
    val rawResponse: String = "",
    val thoughtsTokens: Int? = null
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
     * Example: "?¤ëŠ˜ ?ë¹„ë¡?15000???¼ì–´" -> ExpenseEntity(title="?ë¹„", amount=15000.0, ...)
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

    /**
     * ?˜ë„ ë¶„ì„?´ë‚˜ ?°ì´???„í„°ë§??†ì´ ?œìˆ˜?˜ê²Œ ?€??ë§¥ë½ë§??„ë‹¬?˜ëŠ” ?¤íŠ¸ë¦?(?”ë²„ê·??ìœ ?€?”ìš©)
     */
    fun rawChatStream(
        messages: List<ChatMessageEntity>,
        modelName: String? = null,
        historyLimit: Int = 20,
        images: List<AiImageAttachment> = emptyList()
    ): Flow<ChatResponse>
}

@Serializable
sealed class ChatResponse {
    @Serializable
    data class Chunk(val text: String) : ChatResponse()
    @Serializable
    data class Metadata(
        val promptTokens: Int?,
        val candidatesTokens: Int?,
        val totalTokens: Int?,
        val responseTimeMs: Long,
        val modelName: String,
        val agentVersion: String,
        val provider: String,
        val firstPassPrompt: String? = null,
        val firstPassResponse: String? = null,
        val secondPassPrompt: String? = null,
        val secondPassResponse: String? = null,
        val thoughtsTokens: Int? = null,
        val inputHistoryCount: Int? = null
    ) : ChatResponse()

    /**
     * UI?ì„œ ?¬ìš©???•ì¸???„ìš”???¡ì…˜ ?”ì²­ (?? ?°ì´???? œ/?˜ì • ì»¨íŒ ?¤ì´?¼ë¡œê·?
     */
    @Serializable
    data class ActionRequest(
        val intent: String,
        val action: String,
        val targetItems: List<ExpenseEntity>,
        val reasoning: String,
        val updateField: String? = null,
        val newValue: String? = null
    ) : ChatResponse()

    /**
     * ?¨ìˆœ ?°ì´??ëª©ë¡ ?œì‹œ ?”ì²­ (?ì„¸??ë³´ê¸°??
     */
    @Serializable
    data class ShowDetails(
        val items: List<ExpenseEntity>,
        val title: String
    ) : ChatResponse()
}
