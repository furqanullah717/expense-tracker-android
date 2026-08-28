package com.smartspend.ai.ai.gateway

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.smartspend.ai.ai.model.AiAnalysisReport
import com.smartspend.ai.ai.model.AiMasterRouterResponse
import com.smartspend.ai.ai.model.AiSecondPassResponse
import com.smartspend.ai.ai.AiPromptTemplates
import com.smartspend.ai.data.model.ChatMessageEntity
import com.smartspend.ai.data.model.ExpenseEntity
import com.smartspend.ai.utils.Utils
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.UsageMetadata
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class FirebaseAiGateway @Inject constructor() : AiGateway {
    companion object {
        val TAG = "FirebaseAiGateway"
        val provider get() = AiModelCatalog.provider
        val agentVersion get() = AiModelCatalog.agentVersion
        val selectedModelName get() = AiModelCatalog.modelName
        val useDetailedAnalysis: Boolean = false
        val similarityThreshold: Float = 0.5f
    }

    private val chatModel = Firebase.ai.generativeModel(
        modelName = selectedModelName
    )

    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    override suspend fun parseExpense(input: String, isIncome: Boolean): Result<ExpenseEntity> {
        Log.d(TAG, "parseExpense: input=$input, isIncome=$isIncome")
        val now = Calendar.getInstance()
        val categories = if (isIncome) Utils.incomeCategories else Utils.expenseCategories
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
        
        val referenceCalendar = StringBuilder()
        val refFormat = SimpleDateFormat("yyyy-MM-dd (EEEE)", Locale.US)
        val tempCal = Calendar.getInstance()
        for (i in 0 until 14) {
            referenceCalendar.append("- ${refFormat.format(tempCal.time)}${if (i == 0) " [TODAY]" else ""}\n")
            tempCal.add(Calendar.DAY_OF_YEAR, -1)
        }

        val prompt = AiPromptTemplates.getParseExpensePrompt(
            isIncome = isIncome,
            categories = categories,
            referenceCalendar = referenceCalendar.toString(),
            todayDate = todayDate,
            input = input
        )

        return try {
            val response = chatModel.generateContent(prompt)
            val responseText = response.text ?: throw Exception("Empty response from AI")
            val entityJson = json.decodeFromString<ExpenseEntityJson>(responseText)
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val parsedDate = dateFormat.parse(entityJson.date) ?: now.time
            val resultCal = Calendar.getInstance().apply { time = parsedDate }

            Result.success(
                ExpenseEntity(
                    id = null,
                    title = entityJson.title,
                    amount = entityJson.amount,
                    date = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(resultCal.time),
                    type = entityJson.type,
                    category = entityJson.category
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseExpense error: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun routeIntent(input: String, modelName: String?): Result<AiMasterRouterResultData> {
        Log.d(TAG, "routeIntent: input=$input, model=$modelName")
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
        val prompt = AiPromptTemplates.getMasterRouterPrompt(input, todayDate)

        val targetModelName = modelName ?: selectedModelName
        val currentModel = Firebase.ai.generativeModel(
            modelName = targetModelName,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
            }
        )

        return try {
            val startTime = System.nanoTime()
            val response = currentModel.generateContent(prompt)
            val durationMs = (System.nanoTime() - startTime) / 1_000_000
            
            val responseText = response.text ?: throw Exception("Empty response from AI")
            val usage = response.usageMetadata
            
            val routerResponse = json.decodeFromString<AiMasterRouterResponse>(responseText)
            Result.success(
                AiMasterRouterResultData(
                    response = routerResponse,
                    responseTimeMs = durationMs,
                    promptTokens = usage?.promptTokenCount,
                    candidatesTokens = usage?.candidatesTokenCount,
                    totalTokens = usage?.totalTokenCount,
                    modelName = targetModelName,
                    prompt = prompt, // Restore full prompt with template
                    rawResponse = responseText,
                    thoughtsTokens = usage?.thoughtsTokenCount
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "routeIntent error: ${e.message}", e)
            Result.failure(e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun analyzeSpending(
        history: List<ExpenseEntity>,
        startDate: String?,
        endDate: String?
    ): Result<AiAnalysisResultData> {
        Log.d(TAG, "analyzeSpending: count=${history.size}, period=$startDate ~ $endDate")

        val contextData = getSummarizedHistoryParts(history)
        val periodText = if (startDate != null && endDate != null) "$startDate ~ $endDate" else "?ÑÏ≤¥ Í∏∞Í∞Ñ"
        
        val prompt = if (useDetailedAnalysis) {
            AiPromptTemplates.getAnalyzeDetailSpendingPrompt(
                periodText = periodText,
                monthlyText = contextData.monthlyText,
                categoryText = contextData.categoryText,
                top10Text = contextData.top10Text,
                recentHistoryText = contextData.recentHistoryText,
                comparisonText = contextData.comparisonText,
                yearlyTrendText = contextData.yearlyTrendText
            )
        } else {
            AiPromptTemplates.getAnalyzeSpendingPrompt(
                periodText = periodText,
                monthlyText = contextData.monthlyText,
                categoryText = contextData.categoryText,
                top10Text = contextData.top10Text,
                recentHistoryText = contextData.recentHistoryText,
                comparisonText = contextData.comparisonText,
                yearlyTrendText = contextData.yearlyTrendText
            )
        }

        return try {
            val startTime = System.nanoTime()
            val response = chatModel.generateContent(prompt)
            val durationMs = (System.nanoTime() - startTime) / 1_000_000
            
            val responseText = response.text ?: throw Exception("Empty response from AI")
            val usage = response.usageMetadata
            val promptTokens = usage?.promptTokenCount
            val candidatesTokens = usage?.candidatesTokenCount
            val totalTokens = usage?.totalTokenCount
            val thoughtsTokens = usage?.thoughtsTokenCount
            
            val decodedReport = json.decodeFromString<AiAnalysisReport>(responseText)
            val finalReport = if (useDetailedAnalysis) {
                decodedReport.copy(
                    summary = decodedReport.executiveSummary ?: decodedReport.summary ?: "",
                    insights = decodedReport.keyInsights?.map { "${it.topic}: ${it.finding} (${it.implication})" }
                        ?: decodedReport.insights ?: emptyList(),
                    savingTips = decodedReport.actionableStrategies?.map { "[${it.urgency}] ${it.advice} - ${it.expectedImpact}" }
                        ?: decodedReport.savingTips ?: emptyList()
                )
            } else {
                decodedReport
            }

            Result.success(
                AiAnalysisResultData(
                    report = finalReport,
                    geminiPrompt = prompt,
                    responseTimeMs = durationMs,
                    promptTokens = promptTokens,
                    candidatesTokens = candidatesTokens,
                    totalTokens = totalTokens,
                    modelName = selectedModelName,
                    provider = provider,
                    rawResponseJson = responseText,
                    thoughtsTokens = thoughtsTokens
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "analyzeSpending error: ${e.message}", e)
            Result.failure(e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun chatStream(
        messages: List<ChatMessageEntity>,
        history: List<ExpenseEntity>,
        modelName: String?,
        historyLimit: Int,
        images: List<AiImageAttachment>,
        files: List<AiFileAttachment>
    ): Flow<ChatResponse> = flow {
        Log.d(TAG, "chatStream: Total History Size: ${history.size}")
        val lastMessage = messages.lastOrNull()?.content ?: ""
        
        // 1?®Í≥Ñ: ?òÎèÑ ?åÏïÖ ?úÏûë (?ùÍ∞Å Ï§?.. ?úÏãú)
        emit(ChatResponse.Chunk("?îç ?òÎèÑÎ•?Î∂ÑÏÑù?òÍ≥† ?àÏäµ?àÎã§...\n"))

        // ?Ä??Í∏∞Î°ù Íµ¨ÏÑ± (ÏßÅÏ†Ñ 1Í∞??Ä????Ï∂îÏ∂ú)
        val historyToUse = if (messages.size >= 3) messages.dropLast(1).takeLast(2)
        else if (messages.size == 2) messages.take(1)
        else emptyList()

        val historyContext = historyToUse.joinToString("\n") { 
            "${if (it.role == ChatMessageEntity.ROLE_USER) "User" else "Assistant"}: ${it.content}" 
        }

        // 1. ÎßàÏä§???ºÏö∞?∞Î? ?µÌïú ?∏ÌÖê??Î∂ÑÎ•ò ?òÌñâ (Î¨∏Îß• ?¨Ìï®)
        val routingInput = if (historyContext.isNotBlank()) "Context:\n$historyContext\n\nQuestion: $lastMessage" else lastMessage
        Log.d(TAG, "chatStream: [1st PASS INPUT]\n$routingInput")
        
        val routingResultData = routeIntent(routingInput, modelName).getOrElse { e ->
            Log.e(TAG, "chatStream routing error: ${e.message}")
            emit(ChatResponse.Chunk("\n??**?§Î•òÍ∞Ä Î∞úÏÉù?àÏäµ?àÎã§.**\n?†Ïãú ???§Ïãú ?úÎèÑ?¥Ï£º?∏Ïöî.\n(?¨Ïú†: ${e.message ?: "Internal Server Error"})"))
            return@flow
        }
        var routerRes = routingResultData.response
        Log.d(TAG, "chatStream: [1st PASS RESULT] intent=${routerRes.intent}, start=${routerRes.start_date}, end=${routerRes.end_date}")

        // CONTEXT_REFERENCE Ï≤òÎ¶¨ (Îß•ÎùΩ ?¥ÏÜå)
        if (routerRes.intent == "CONTEXT_REFERENCE") {
            emit(ChatResponse.Chunk("?îÑ ?¥Ï†Ñ ?Ä?îÎ? ?ïÏù∏?òÏó¨ ?îÏ≤≠???¥ÏÑù?òÍ≥† ?àÏäµ?àÎã§...\n"))
            val resolutionPrompt = AiPromptTemplates.getContextResolutionPrompt(historyContext, lastMessage, todayDate(), similarityThreshold)
            
            try {
                val resolutionRes = chatModel.generateContent(resolutionPrompt)
                val resolutionText = resolutionRes.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.let { 
                    (it as? TextPart)?.text 
                } ?: ""
                Log.d(TAG, "chatStream: [CONTEXT RESOLUTION RESULT]\n$resolutionText")
                
                routerRes = json.decodeFromString<AiMasterRouterResponse>(resolutionText)
            } catch (e: Exception) {
                Log.e(TAG, "Context resolution error: ${e.message}")
                emit(ChatResponse.Chunk("\n??**Îß•ÎùΩ Î∂ÑÏÑù Ï§??§Î•òÍ∞Ä Î∞úÏÉù?àÏäµ?àÎã§.**\n?§Ïãú ÏßàÎ¨∏?¥Ï£º?úÎ©¥ Í∞êÏÇ¨?òÍ≤†?µÎãà??"))
                return@flow
            }
        }

        // 2. ÏµúÏ¢Ö ?∏ÌÖê???§Ìñâ
        handleIntent(routerRes, routingResultData, lastMessage, history, historyToUse.size)
    }

    override fun rawChatStream(
        messages: List<ChatMessageEntity>,
        modelName: String?,
        historyLimit: Int,
        images: List<AiImageAttachment>
    ): Flow<ChatResponse> = flow {
        val targetModelName = modelName ?: selectedModelName
        val currentModel = Firebase.ai.generativeModel(modelName = targetModelName)
        
        // ÎßàÏ?Îß?Î©îÏãúÏßÄ???ÑÏû¨ ÏßàÎ¨∏?¥Î?Î°??úÏô∏
        val lastMsg = messages.lastOrNull() ?: return@flow
        val previousMessages = messages.dropLast(1)
        
        // historyLimit ?ÅÏö© (?? -1?Ä ?ÑÏ≤¥ ?ÑÏÜ°)
        val messagesToUse = if (historyLimit > 0) {
            previousMessages.takeLast(historyLimit)
        } else {
            previousMessages
        }
        
        // ChatHistory Íµ¨ÏÑ±
        val history = messagesToUse.map { msg ->
            content(if (msg.role == ChatMessageEntity.ROLE_USER) "user" else "model") {
                text(msg.content)
            }
        }
        
        val chatSession = currentModel.startChat(history)
        
        var fullText = ""
        val startTime = System.nanoTime()
        var lastResponse: GenerateContentResponse? = null
        
        try {
            // ÎßàÏ?Îß?ÏßàÎ¨∏ ?ÑÏÜ°
            chatSession.sendMessageStream(lastMsg.content).collect { chunk ->
                chunk.text?.let { 
                    fullText += it
                    emit(ChatResponse.Chunk(it)) 
                }
                lastResponse = chunk
            }
        } catch (e: Exception) {
            emit(ChatResponse.Chunk("\n??[RAW_CHAT_ERROR]: ${e.message}"))
        }

        val usage = lastResponse?.usageMetadata
        emit(ChatResponse.Metadata(
            promptTokens = usage?.promptTokenCount,
            candidatesTokens = usage?.candidatesTokenCount,
            totalTokens = usage?.totalTokenCount,
            responseTimeMs = (System.nanoTime() - startTime) / 1_000_000,
            modelName = targetModelName,
            agentVersion = agentVersion,
            provider = provider,
            firstPassPrompt = messagesToUse.joinToString("\n---\n") { "${it.role}: ${it.content}" } + "\n\n>>> CURRENT: ${lastMsg.content}",
            firstPassResponse = fullText,
            thoughtsTokens = usage?.thoughtsTokenCount,
            inputHistoryCount = messagesToUse.size
        ))
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun FlowCollector<ChatResponse>.handleIntent(
        routerRes: AiMasterRouterResponse,
        routingData: AiMasterRouterResultData,
        lastMessage: String,
        history: List<ExpenseEntity>,
        inputHistoryCount: Int
    ) {
        when (routerRes.intent) {
            "DATA_RETRIEVAL" -> {
                emit(ChatResponse.Chunk("?ìä Í∞ÄÍ≥ÑÎ? ?∞Ïù¥?∞Î? Ï°∞Ìöå?òÍ≥† Í≥ÑÏÇ∞ Ï§ëÏûÖ?àÎã§...\n"))
                val filteredHistory = filterHistory(history, routerRes)
                    .sortedByDescending { item ->
                        try {
                            SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(item.date.trim())?.time ?: 0L
                        } catch (e: Exception) { 0L }
                    }
                
                var finalItems = filteredHistory
                var secondPassText = ""
                var durationMs2 = 0L
                var usage2: UsageMetadata? = null
                var secondPassPrompt = ""
                var reasoning = routerRes.reasoning

                if (!routerRes.is_bulk) {
                    val uniqueNames = filteredHistory.map { it.title }.distinct()
                    val startTime2 = System.nanoTime()
                    secondPassPrompt = AiPromptTemplates.getSecondPassRetrievalPrompt(lastMessage, uniqueNames)
                    
                    val secondPassRes = try {
                        chatModel.generateContent(secondPassPrompt)
                    } catch (e: Exception) {
                        emit(ChatResponse.Chunk("\n??**?∞Ïù¥??Ï°∞Ìöå Ï§??úÎ≤Ñ ?§Î•òÍ∞Ä Î∞úÏÉù?àÏäµ?àÎã§.**\n?†Ïãú ???§Ïãú ?úÎèÑ?¥Ï£º?∏Ïöî."))
                        return@handleIntent
                    }
                    
                    durationMs2 = (System.nanoTime() - startTime2) / 1_000_000
                    secondPassText = secondPassRes.text ?: ""
                    usage2 = secondPassRes.usageMetadata
                    
                    val retrievalResult = try {
                        json.decodeFromString<AiSecondPassResponse>(secondPassText)
                    } catch (e: Exception) { null }

                    if (retrievalResult != null) {
                        finalItems = filteredHistory.filter { it.title in retrievalResult.relevant_names }
                        reasoning = retrievalResult.reasoning ?: reasoning
                    }
                }

                val operation = routerRes.retrieval_operation ?: "LIST"
                val resultMessage = when (operation) {
                    "SUM" -> {
                        val sum = finalItems.sumOf { it.amount }
                        "Ï¥ùÏï°?Ä **${Utils.formatCurrency(sum)}**?ÖÎãà?? (Î∂ÑÏÑù ?Ä?? ${finalItems.size}Í±?"
                    }
                    "MAX" -> {
                        val maxItem = finalItems.maxByOrNull { it.amount }
                        if (maxItem != null) "Í∞Ä????Í∏àÏï°?Ä **${maxItem.title}**??**${Utils.formatCurrency(maxItem.amount)}**?ÖÎãà??"
                        else "?¥Îãπ?òÎäî ?¥Ïó≠??Ï∞æÏùÑ ???ÜÏäµ?àÎã§."
                    }
                    "MIN" -> {
                        val minItem = finalItems.minByOrNull { it.amount }
                        if (minItem != null) "Í∞Ä???ëÏ? Í∏àÏï°?Ä **${minItem.title}**??**${Utils.formatCurrency(minItem.amount)}**?ÖÎãà??"
                        else "?¥Îãπ?òÎäî ?¥Ïó≠??Ï∞æÏùÑ ???ÜÏäµ?àÎã§."
                    }
                    "COUNT" -> "?¥Îãπ?òÎäî ?¥Ïó≠?Ä Ï¥?**${finalItems.size}Í±?*?ÖÎãà??"
                    "LIST" -> {
                        val displayItems = finalItems.take(10)
                        val listText = displayItems.joinToString("\n") { "- ${it.date}: ${it.title} (${Utils.formatCurrency(it.amount)})" }
                        val suffix = if (finalItems.size > 10) "\n\n...??${finalItems.size - 10}Í±¥Ïù¥ ???àÏäµ?àÎã§. [?êÏÑ∏??Î≥¥Í∏∞]" else ""
                        if (finalItems.isNotEmpty()) "?îÏ≤≠?òÏã† ?¥Ïó≠ Î¶¨Ïä§?∏ÏûÖ?àÎã§:\n\n$listText$suffix" else "Ï°∞Ìöå???¥Ïó≠???ÜÏäµ?àÎã§."
                    }
                    else -> "Ï°∞Í±¥??ÎßûÎäî ?∞Ïù¥?∞Î? Ï∞æÏïòÏßÄÎß??∞ÏÇ∞ Ï¢ÖÎ•òÎ•?Í≤∞Ï†ï?òÏ? Î™ªÌñà?µÎãà??"
                }
                emit(ChatResponse.Chunk("\n**AI ?µÎ?:**\n$resultMessage\n\n> ?í° **AI ?êÎã® Í∑ºÍ±∞:** $reasoning"))
                
                if (operation == "LIST") {
                    emit(ChatResponse.ShowDetails(finalItems, "Ï°∞Ìöå ?¥Ïó≠ ?ÅÏÑ∏"))
                }

                emit(
                    ChatResponse.Metadata(
                    promptTokens = (routingData.promptTokens ?: 0) + (usage2?.promptTokenCount ?: 0),
                    candidatesTokens = (routingData.candidatesTokens ?: 0) + (usage2?.candidatesTokenCount ?: 0),
                    totalTokens = (routingData.totalTokens ?: 0) + (usage2?.totalTokenCount ?: 0),
                    responseTimeMs = routingData.responseTimeMs + durationMs2,
                    modelName = routingData.modelName,
                    agentVersion = agentVersion,
                    provider = provider,
                    firstPassPrompt = lastMessage, // Real input only
                    firstPassResponse = routingData.rawResponse,
                    secondPassPrompt = secondPassPrompt,
                    secondPassResponse = secondPassText,
                    thoughtsTokens = (routingData.thoughtsTokens ?: 0) + (usage2?.thoughtsTokenCount ?: 0),
                    inputHistoryCount = inputHistoryCount
                ))
            }

            "DATA_MANIPULATION" -> {
                emit(ChatResponse.Chunk("?ôÔ∏è ?îÏ≤≠?òÏã† ?ëÏóÖ???ïÎ¶¨?òÍ≥† ?àÏäµ?àÎã§...\n"))
                val filteredHistory = filterHistory(history, routerRes)
                    .sortedByDescending { item ->
                        try {
                            SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(item.date.trim())?.time ?: 0L
                        } catch (e: Exception) { 0L }
                    }
                
                var targetItems = filteredHistory
                var secondPassText = ""
                var durationMs2 = 0L
                var usage2: UsageMetadata? = null
                var secondPassPrompt = ""
                var reasoning = routerRes.reasoning
                var updateField: String? = null
                var manipulationResult: AiSecondPassResponse? = null

                if (!routerRes.is_bulk) {
                    val uniqueNames = filteredHistory.map { it.title }.distinct()
                    val startTime2 = System.nanoTime()
                    secondPassPrompt = AiPromptTemplates.getSecondPassManipulationPrompt(lastMessage, uniqueNames)
                    
                    val secondPassRes = try {
                        chatModel.generateContent(secondPassPrompt)
                    } catch (e: Exception) {
                        emit(ChatResponse.Chunk("\n??**?ëÏóÖ Î∂ÑÏÑù Ï§??úÎ≤Ñ ?§Î•òÍ∞Ä Î∞úÏÉù?àÏäµ?àÎã§.**\n?†Ïãú ???§Ïãú ?úÎèÑ?¥Ï£º?∏Ïöî."))
                        return@handleIntent
                    }
                    
                    durationMs2 = (System.nanoTime() - startTime2) / 1_000_000
                    secondPassText = secondPassRes.text ?: ""
                    usage2 = secondPassRes.usageMetadata
                    
                    manipulationResult = try {
                        json.decodeFromString<AiSecondPassResponse>(secondPassText)
                    } catch (e: Exception) { null }

                    if (manipulationResult != null) {
                        targetItems = filteredHistory.filter { it.title in manipulationResult.relevant_names }
                        reasoning = manipulationResult.reasoning ?: reasoning
                        updateField = manipulationResult.update_field
                    }
                }

                val action = routerRes.manipulation_type ?: "DELETE"
                val actionText = when (action) {
                    "DELETE" -> "??†ú"
                    "UPDATE" -> "?òÏ†ï (${updateField ?: "?ÑÎìú"} Î≥ÄÍ≤?"
                    "INSERT" -> "Ï∂îÍ?"
                    else -> "Ï≤òÎ¶¨"
                }
                
                val displayItems = targetItems.take(10)
                val targetListText = displayItems.joinToString("\n") { "- ${it.date}: ${it.title} (${Utils.formatCurrency(it.amount)})" }
                val suffix = if (targetItems.size > 10) "\n\n...??${targetItems.size - 10}Í±¥Ïù¥ ???àÏäµ?àÎã§. [?êÏÑ∏??Î≥¥Í∏∞]" else ""

                emit(ChatResponse.Chunk("\n**AI ?µÎ?:**\n?§Ïùå ??™©?§Ïóê ?Ä??**$actionText** ?ëÏóÖ??ÏßÑÌñâ?†Íπå?? (?Ä?? ${targetItems.size}Í±?\n\n$targetListText$suffix\n\n> ?í° **?¥Ïú†:** $reasoning"))
                
                emit(ChatResponse.ShowDetails(targetItems, "?ÅÏÑ∏ ?¥Ïó≠"))

                emit(
                    ChatResponse.ActionRequest(
                    intent = routerRes.intent,
                    action = action,
                    targetItems = targetItems,
                    reasoning = reasoning,
                    updateField = updateField,
                    newValue = manipulationResult?.new_value
                ))

                emit(
                    ChatResponse.Metadata(
                    promptTokens = (routingData.promptTokens ?: 0) + (usage2?.promptTokenCount ?: 0),
                    candidatesTokens = (routingData.candidatesTokens ?: 0) + (usage2?.candidatesTokenCount ?: 0),
                    totalTokens = (routingData.totalTokens ?: 0) + (usage2?.totalTokenCount ?: 0),
                    responseTimeMs = routingData.responseTimeMs + durationMs2,
                    modelName = routingData.modelName,
                    agentVersion = agentVersion,
                    provider = provider,
                    firstPassPrompt = routingData.prompt,
                    firstPassResponse = routingData.rawResponse,
                    secondPassPrompt = secondPassPrompt,
                    secondPassResponse = secondPassText,
                    thoughtsTokens = (routingData.thoughtsTokens ?: 0) + (usage2?.thoughtsTokenCount ?: 0),
                    inputHistoryCount = inputHistoryCount
                ))
            }
            
            "DATA_ANALYSIS" -> {
                emit(ChatResponse.Chunk("?ìù ?∞Ïù¥?∞Î? ?ïÎ? Î∂ÑÏÑù?òÍ≥† ?úÏñ∏???ëÏÑ± Ï§ëÏûÖ?àÎã§...\n"))
                val filteredHistory = filterHistory(history, routerRes)
                    .sortedByDescending { item ->
                        try {
                            SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(item.date.trim())?.time ?: 0L
                        } catch (e: Exception) { 0L }
                    }
                val historyDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)
                val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.US)
                val monthlySummaryMap = filteredHistory.groupBy {
                    try { monthKeyFormat.format(historyDateFormat.parse(it.date.trim())!!) } catch(e: Exception) { "Unknown" }
                }.mapValues { (_, items) ->
                    val income = items.filter { it.type.trim().equals("Income", ignoreCase = true) }.sumOf { it.amount }
                    val expense = items.filter { it.type.trim().equals("Expense", ignoreCase = true) }.sumOf { it.amount }
                    "?òÏûÖ: ${Utils.formatCurrency(income)} | ÏßÄÏ∂? ${Utils.formatCurrency(expense)} (Ï¥?${items.size}Í±?"
                }
                val summaryTableText = monthlySummaryMap.entries.sortedBy { it.key }.joinToString("\n") { "- ${it.key}: ${it.value}" }
                val rawDataText = filteredHistory.joinToString("\n") { "${it.date} | ${it.category} | ${it.title} | ${it.amount}?? }
                val combinedDataContext = "### [Monthly Summary Table]\n$summaryTableText\n\n### [Detailed Transaction Data]\n$rawDataText"

                val startTime2 = System.nanoTime()
                val secondPassPrompt = AiPromptTemplates.getSecondPassAnalysisPrompt(lastMessage, combinedDataContext)
                emit(ChatResponse.Chunk("\n**AI ?µÎ?:**\n"))
                
                var lastResponse: GenerateContentResponse? = null
                try {
                    chatModel.generateContentStream(secondPassPrompt).collect { chunk ->
                        chunk.text?.let { emit(ChatResponse.Chunk(it)) }
                        lastResponse = chunk
                    }
                } catch (e: Exception) {
                    emit(ChatResponse.Chunk("\n??**?∞Ïù¥??Î∂ÑÏÑù ?§Ìä∏Î¶¨Î∞ç Ï§??§Î•òÍ∞Ä Î∞úÏÉù?àÏäµ?àÎã§.**"))
                }
                
                val durationMs2 = (System.nanoTime() - startTime2) / 1_000_000
                val usage2 = lastResponse?.usageMetadata
                emit(
                    ChatResponse.Metadata(
                    promptTokens = (routingData.promptTokens ?: 0) + (usage2?.promptTokenCount ?: 0),
                    candidatesTokens = (routingData.candidatesTokens ?: 0) + (usage2?.candidatesTokenCount ?: 0),
                    totalTokens = (routingData.totalTokens ?: 0) + (usage2?.totalTokenCount ?: 0),
                    responseTimeMs = routingData.responseTimeMs + durationMs2,
                    modelName = routingData.modelName,
                    agentVersion = agentVersion,
                    provider = provider,
                    firstPassPrompt = routingData.prompt,
                    firstPassResponse = routingData.rawResponse,
                    secondPassPrompt = secondPassPrompt,
                    secondPassResponse = lastResponse?.text ?: "",
                    thoughtsTokens = (routingData.thoughtsTokens ?: 0) + (usage2?.thoughtsTokenCount ?: 0),
                    inputHistoryCount = inputHistoryCount
                ))
            }

            "MERMAID_ERROR" -> {
                emit(ChatResponse.Chunk("?õ†Ô∏?Mermaid ?§Ïù¥?¥Í∑∏???§Î•òÎ•?Î∂ÑÏÑù?òÏó¨ ?òÏ†ï Ï§ëÏûÖ?àÎã§...\n"))
                val startTime2 = System.nanoTime()
                
                val fixPrompt = """
                    You are a Mermaid.js expert. The user reported a rendering error in a diagram.
                    Fix the Mermaid code so it becomes syntactically valid and renders correctly.
                    
                    User's error report and original code:
                    $lastMessage
                    
                    Return the corrected code block (starting with ```mermaid) and a very brief explanation in Korean of what was fixed.
                """.trimIndent()
                
                var secondPassUsage: UsageMetadata? = null
                val response = try {
                    // JSON ?úÏïΩ???ÜÎäî chatModel???¨Ïö©?òÏó¨ ?êÏó∞?§Îü¨??ÎßàÌÅ¨?§Ïö¥ ?ëÎãµ??Î∞õÏäµ?àÎã§.
                    val res = chatModel.generateContent(fixPrompt)
                    secondPassUsage = res.usageMetadata
                    res.text ?: "?§Î•òÎ•??òÏ†ï?òÏ? Î™ªÌñà?µÎãà??"
                } catch (e: Exception) {
                    emit(ChatResponse.Chunk("\n??**?§Î•ò ?òÏ†ï Ï§??úÎ≤Ñ ?êÎü¨Í∞Ä Î∞úÏÉù?àÏäµ?àÎã§.**"))
                    return@handleIntent
                }

                emit(ChatResponse.Chunk("\n**?òÏ†ï??Í≤∞Í≥º:**\n$response"))

                emit(
                    ChatResponse.Metadata(
                    promptTokens = (routingData.promptTokens ?: 0) + (secondPassUsage?.promptTokenCount ?: 0),
                    candidatesTokens = (routingData.candidatesTokens ?: 0) + (secondPassUsage?.candidatesTokenCount ?: 0),
                    totalTokens = (routingData.totalTokens ?: 0) + (secondPassUsage?.totalTokenCount ?: 0),
                    responseTimeMs = routingData.responseTimeMs + (System.nanoTime() - startTime2) / 1_000_000,
                    modelName = routingData.modelName,
                    agentVersion = agentVersion,
                    provider = provider,
                    firstPassPrompt = routingData.prompt,
                    firstPassResponse = routingData.rawResponse,
                    secondPassPrompt = fixPrompt,
                    secondPassResponse = response,
                    thoughtsTokens = (routingData.thoughtsTokens ?: 0) + (secondPassUsage?.thoughtsTokenCount ?: 0),
                    inputHistoryCount = inputHistoryCount
                ))
            }
            
            "SIMPLE_RESPONSE", "FALLBACK" -> {
                emit(ChatResponse.Chunk("?í¨ ?µÎ????ëÏÑ±?òÍ≥† ?àÏäµ?àÎã§...\n"))
                val startTime2 = System.nanoTime()
                var secondPassUsage: UsageMetadata? = null
                var secondPassPromptUsed: String? = null
                val response = routerRes.reply_message ?: run {
                    val historyText = getSummarizedHistoryParts(history).toFullMarkdown()
                    val systemPrompt = AiPromptTemplates.getChatPrompt(historyText, todayDate())
                    val prompt = "$systemPrompt\n\nUser Question: $lastMessage"
                    secondPassPromptUsed = prompt
                    val res = try {
                        chatModel.generateContent(prompt)
                    } catch (e: Exception) {
                        emit(ChatResponse.Chunk("\n??**?µÎ? ?ùÏÑ± Ï§??§Î•òÍ∞Ä Î∞úÏÉù?àÏäµ?àÎã§.**"))
                        return@handleIntent
                    }
                    secondPassUsage = res.usageMetadata
                    res.text ?: "Ï£ÑÏÜ°?©Îãà?? ?¥Ìï¥?òÏ? Î™ªÌñà?µÎãà??"
                }
                emit(ChatResponse.Chunk("\n**AI ?µÎ?:**\n$response"))
                emit(
                    ChatResponse.Metadata(
                    promptTokens = (routingData.promptTokens ?: 0) + (secondPassUsage?.promptTokenCount ?: 0),
                    candidatesTokens = (routingData.candidatesTokens ?: 0) + (secondPassUsage?.candidatesTokenCount ?: 0),
                    totalTokens = (routingData.totalTokens ?: 0) + (secondPassUsage?.totalTokenCount ?: 0),
                    responseTimeMs = routingData.responseTimeMs + (System.nanoTime() - startTime2) / 1_000_000,
                    modelName = routingData.modelName,
                    agentVersion = agentVersion,
                    provider = provider,
                    firstPassPrompt = routingData.prompt,
                    firstPassResponse = routingData.rawResponse,
                    secondPassPrompt = secondPassPromptUsed,
                    secondPassResponse = response,
                    thoughtsTokens = (routingData.thoughtsTokens ?: 0) + (secondPassUsage?.thoughtsTokenCount ?: 0),
                    inputHistoryCount = inputHistoryCount
                ))
            }
            
            "DATA_VISUALIZATION" -> {
                emit(ChatResponse.Chunk("?ìä ?∞Ïù¥?∞Î? ?úÍ∞Å?îÌïò???§Ïù¥?¥Í∑∏?®ÏúºÎ°??ïÎ¶¨ Ï§ëÏûÖ?àÎã§...\n"))
                val filteredHistory = filterHistory(history, routerRes)
                val rawDataText = filteredHistory.joinToString("\n") { "${it.date} | ${it.category} | ${it.title} | ${it.amount}?? }
                
                val prompt = """
                    Based on the following spending data, create a helpful Mermaid.js diagram (e.g., pie chart, flow chart, or gantt) to visualize the user's spending habits or flow.
                    
                    Data:
                    $rawDataText
                    
                    Instructions:
                    1. Choose the most appropriate Mermaid diagram type.
                    2. Provide the Mermaid code block starting with ```mermaid.
                    3. Add a brief explanation in Korean about what the diagram shows.
                    4. Current User Question: "$lastMessage"
                """.trimIndent()

                val startTime2 = System.nanoTime()
                var secondPassUsage: UsageMetadata? = null
                val response = try {
                    val res = chatModel.generateContent(prompt)
                    secondPassUsage = res.usageMetadata
                    res.text ?: "?úÍ∞Å?îÏóê ?§Ìå®?àÏäµ?àÎã§."
                } catch (e: Exception) {
                    emit(ChatResponse.Chunk("\n??**?úÍ∞Å???ùÏÑ± Ï§??§Î•òÍ∞Ä Î∞úÏÉù?àÏäµ?àÎã§.**"))
                    return@handleIntent
                }

                emit(ChatResponse.Chunk("\n**?úÍ∞Å??Í≤∞Í≥º:**\n$response"))

                emit(
                    ChatResponse.Metadata(
                    promptTokens = (routingData.promptTokens ?: 0) + (secondPassUsage?.promptTokenCount ?: 0),
                    candidatesTokens = (routingData.candidatesTokens ?: 0) + (secondPassUsage?.candidatesTokenCount ?: 0),
                    totalTokens = (routingData.totalTokens ?: 0) + (secondPassUsage?.totalTokenCount ?: 0),
                    responseTimeMs = routingData.responseTimeMs + (System.nanoTime() - startTime2) / 1_000_000,
                    modelName = routingData.modelName,
                    agentVersion = agentVersion,
                    provider = provider,
                    firstPassPrompt = routingData.prompt,
                    firstPassResponse = routingData.rawResponse,
                    secondPassPrompt = prompt,
                    secondPassResponse = response,
                    thoughtsTokens = (routingData.thoughtsTokens ?: 0) + (secondPassUsage?.thoughtsTokenCount ?: 0),
                    inputHistoryCount = inputHistoryCount
                ))
            }

            "APP_ACTION" -> {
                val action = routerRes.sub_categories.firstOrNull() ?: "UNKNOWN"
                emit(ChatResponse.Chunk("?? **??Í∏∞Îä•???§Ìñâ?©Îãà??** `$action`"))
                
                emit(
                    ChatResponse.ActionRequest(
                    intent = routerRes.intent,
                    action = action,
                    targetItems = emptyList(),
                    reasoning = routerRes.reasoning
                ))

                emit(
                    ChatResponse.Metadata(
                    promptTokens = routingData.promptTokens,
                    candidatesTokens = routingData.candidatesTokens,
                    totalTokens = routingData.totalTokens,
                    responseTimeMs = routingData.responseTimeMs,
                    modelName = routingData.modelName,
                    agentVersion = agentVersion,
                    provider = provider,
                    firstPassPrompt = routingData.prompt,
                    firstPassResponse = routingData.rawResponse,
                    thoughtsTokens = routingData.thoughtsTokens,
                    inputHistoryCount = inputHistoryCount
                ))
            }

            else -> {
                emit(ChatResponse.Chunk("??**?ïÏùò?òÏ? ?äÏ? ?∏ÌÖê?∏ÏûÖ?àÎã§.**"))
            }
        }
    }

    private fun todayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
    }

    private fun filterHistory(history: List<ExpenseEntity>, routerRes: AiMasterRouterResponse): List<ExpenseEntity> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val historyDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        
        // AIÍ∞Ä ?†ÏßúÎ•??òÎ™ª ?òÍ≤º??Í≤ΩÏö∞Î•??ÄÎπÑÌï¥ try-catchÎ°??àÏ†Ñ?òÍ≤å Ï≤òÎ¶¨
        val start = routerRes.start_date?.let { try { dateFormat.parse(it) } catch(e: Exception) { null } }
        val end = routerRes.end_date?.let { try { dateFormat.parse(it) } catch(e: Exception) { null } }
        
        val filtered = history.filter { item ->
            // ?†Ïßú Î¨∏Ïûê??Í≥µÎ∞± ?úÍ±∞ Î∞??†Ïó∞???åÏã±
            val dateStr = item.date.trim()
            val itemDate = try { 
                historyDateFormat.parse(dateStr) 
            } catch (e: Exception) { 
                // ?ïÏãù???§Î? Í≤ΩÏö∞(?? ?®Ïùº ?´Ïûê d/M/yyyy) ?ÄÎπ?
                try { 
                    SimpleDateFormat("d/M/yyyy", Locale.US).parse(dateStr) 
                } catch(_: Exception) {
                    Log.e(TAG, "filterHistory: Fatal parsing error for date '$dateStr'")
                    null 
                }
            }
            
            // Í∏∞Í∞Ñ ?ÑÌÑ∞Îß? ?†ÏßúÍ∞Ä ?òÎÇò?ºÎèÑ ?ÜÏúºÎ©?AIÍ∞Ä Í∏∞Í∞Ñ ÏßÄ?ïÏùÑ ???àÏúºÎ©? ?ÑÏ≤¥ ?¨Ìï®
            val dateMatch = if (start != null && end != null && itemDate != null) {
                !itemDate.before(start) && !itemDate.after(end)
            } else true
            
            // Ïπ¥ÌÖåÍ≥†Î¶¨ ?ÑÌÑ∞Îß? Ïπ¥ÌÖåÍ≥†Î¶¨ Î¶¨Ïä§?∏Í? ÎπÑÏñ¥?àÏúºÎ©??ÑÏ≤¥ ?¨Ìï®
            val categoryMatch = if (routerRes.sub_categories.isNotEmpty()) {
                routerRes.sub_categories.any { it.trim().equals(item.category.trim(), ignoreCase = true) }
            } else true
            
            dateMatch && categoryMatch
        }
        
        Log.d(TAG, "filterHistory: [FILTER] Start: $start, End: $end, Categories: ${routerRes.sub_categories}, Result Count: ${filtered.size}")
        return filtered
    }

    private data class SummarizedContext(
        val monthlyText: String,
        val categoryText: String,
        val top10Text: String,
        val recentHistoryText: String,
        val comparisonText: String,
        val yearlyTrendText: String
    ) {
        fun toFullMarkdown(): String {
            return """
                ## 1. Monthly Summary
                $monthlyText
                
                ## 2. Category Distribution
                $categoryText
                
                ## 3. High-Value Spending (Top 10)
                $top10Text
                
                ## 4. Recent 30 Days Detailed History
                $recentHistoryText
                
                ## 5. Month-over-Month Comparison (Run-rate)
                $comparisonText
                
                ${if (yearlyTrendText.isNotBlank()) "## 6. Yearly Trend Analysis\n$yearlyTrendText" else ""}
            """.trimIndent()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getSummarizedHistoryParts(history: List<ExpenseEntity>): SummarizedContext {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        val monthFormat = SimpleDateFormat("yyyy-MM", Locale.US)
        val thirtyDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.time

        val monthlySummary = history.groupBy {
            try { monthFormat.format(dateFormat.parse(it.date.trim())!!) } catch (_: Exception) { "Unknown" }
        }.mapValues { (_, items) ->
            val income = items.filter { it.type.trim().equals("Income", ignoreCase = true) }.sumOf { it.amount }
            val expense = items.filter { it.type.trim().equals("Expense", ignoreCase = true) }.sumOf { it.amount }
            Pair(income, expense)
        }

        val expensesOnly = history.filter { it.type.trim().equals("Expense", ignoreCase = true) }
        val totalExpenseAmount = expensesOnly.sumOf { it.amount }
        val categorySummary = expensesOnly.groupBy { it.category.ifBlank { it.title }.trim() }
            .mapValues { (_, items) ->
                val amount = items.sumOf { it.amount }
                val percentage = if (totalExpenseAmount > 0) (amount / totalExpenseAmount) * 100 else 0.0
                Pair(amount, percentage)
            }

        val top10Expenses = expensesOnly.sortedByDescending { it.amount }.take(10)

        val recentHistory = history.filter {
            try {
                val date = dateFormat.parse(it.date.trim())
                date != null && date.after(thirtyDaysAgo)
            } catch (_: Exception) { false }
        }

        val cal = Calendar.getInstance()
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val currentMonthKey = monthFormat.format(cal.time)
        val monthlyComparisonMap = history.filter { it.type.trim().equals("Expense", ignoreCase = true) }
            .groupBy { try { monthFormat.format(dateFormat.parse(it.date.trim())!!) } catch (_: Exception) { "Unknown" } }
            .filter { it.key != "Unknown" }
            .mapValues { (_, items) ->
                items.filter {
                    try {
                        val itemDate = dateFormat.parse(it.date.trim())!!
                        val itemCal = Calendar.getInstance().apply { time = itemDate }
                        itemCal.get(Calendar.DAY_OF_MONTH) <= currentDay
                    } catch (_: Exception) { false }
                }.sumOf { it.amount }
            }

        val sortedComparison = monthlyComparisonMap.entries.sortedBy { it.key }
        val comparisonListText = sortedComparison.joinToString("\n") { (month, amount) ->
            val suffix = if (month == currentMonthKey) " (?¥Î≤à ??" else ""
            "- $month (1??${currentDay}??: ??{String.format(Locale.getDefault(), "%,.0f", amount)}$suffix"
        }
        
        cal.time = Calendar.getInstance().time
        cal.add(Calendar.MONTH, -1)
        val lastMonthKey = monthFormat.format(cal.time)
        val currentMonthAmount = monthlyComparisonMap[currentMonthKey] ?: 0.0
        val lastMonthAmount = monthlyComparisonMap[lastMonthKey] ?: 0.0
        val summaryStatus = if (lastMonthAmount > 0) {
            val diff = currentMonthAmount - lastMonthAmount
            val percent = (diff / lastMonthAmount) * 100
            if (diff > 0) "ÏßÄ?????ôÍ∏∞ ?ÄÎπ?${String.format(Locale.getDefault(), "%.1f", percent)}% Ï¶ùÍ?"
            else "ÏßÄ?????ôÍ∏∞ ?ÄÎπ?${String.format(Locale.getDefault(), "%.1f", abs(percent))}% Í∞êÏÜå"
        } else "ÎπÑÍµê ?∞Ïù¥??Î∂ÄÏ°?

        val comparisonText = "[Í∏∞Ï?: Îß§Ïõî 1??~ ${currentDay}???ÑÏ†Å ÏßÄÏ∂?\n$comparisonListText\n* ?îÏïΩ: $summaryStatus"

        val yearFormat = SimpleDateFormat("yyyy", Locale.US)
        val yearlySummary = expensesOnly.groupBy { try { yearFormat.format(dateFormat.parse(it.date.trim())!!) } catch (_: Exception) { "Unknown" } }
            .filter { it.key != "Unknown" }
            .mapValues { (_, items) ->
                val totalYearExpense = items.sumOf { it.amount }
                val activeMonths = items.map { try { monthFormat.format(dateFormat.parse(it.date.trim())!!) } catch (_: Exception) { "" } }.distinct().filter { it.isNotBlank() }.size
                val monthlyAverage = if (activeMonths > 0) totalYearExpense / activeMonths else 0.0
                Triple(totalYearExpense, activeMonths, monthlyAverage)
            }

        val yearlyTrendText = if (yearlySummary.size >= 2) {
            yearlySummary.entries.sortedBy { it.key }.joinToString("\n") { "- ${it.key}?? ???âÍ∑† ÏßÄÏ∂???{String.format(Locale.getDefault(), "%,.0f", it.value.third)} (Ï¥???{String.format(Locale.getDefault(), "%,.0f", it.value.first)} / ${it.value.second}Í∞úÏõî)" }
        } else ""

        val monthlyText = monthlySummary.entries.sortedBy { it.key }.joinToString("\n") {
            val (income, expense) = it.value
            val incomeStr = if (income > 0) "Ï¥??òÏûÖ ??{String.format(Locale.getDefault(), "%,.0f", income)} / " else ""
            "- ${it.key}: ${incomeStr}Ï¥?ÏßÄÏ∂???{String.format(Locale.getDefault(), "%,.0f", expense)}"
        }

        val categoryText = categorySummary.entries.sortedByDescending { it.value.second }.joinToString("\n") {
            "- ${it.key}: ??{String.format(Locale.getDefault(), "%,.0f", it.value.first)} (${String.format(Locale.getDefault(), "%.1f", it.value.second)}%)"
        }

        val top10Text = if (top10Expenses.isEmpty()) "Í≥†Ïï° ÏßÄÏ∂??¥Ïó≠???ÜÏäµ?àÎã§." else top10Expenses.withIndex().joinToString("\n") { (index, it) ->
            val parts = it.date.split("/")
            val isoDate = if (parts.size == 3) "${parts[2]}-${parts[1]}-${parts[0]}" else it.date
            "${index + 1}. date: $isoDate | category: ${it.category.ifBlank { "ÎØ∏Ï??? }} | title: ${it.title} | amount: ${String.format(Locale.getDefault(), "%,.0f", it.amount)} ??
        }

        val recentHistoryText = if (recentHistory.isEmpty()) "ÏµúÍ∑º 30?ºÍ∞Ñ Í±∞Îûò ?¥Ïó≠???ÜÏäµ?àÎã§." else recentHistory.withIndex().joinToString("\n") { (index, it) ->
            val parts = it.date.split("/")
            val isoDate = if (parts.size == 3) "${parts[2]}-${parts[1]}-${parts[0]}" else it.date
            "${index + 1}. date: $isoDate | category: ${it.category.ifBlank { "ÎØ∏Ï??? }} | title: ${it.title} | type: ${it.type} | amount: ${String.format(Locale.getDefault(), "%,.0f", it.amount)} ??
        }

        return SummarizedContext(monthlyText, categoryText, top10Text, recentHistoryText, comparisonText, yearlyTrendText)
    }
}

@OptIn(InternalSerializationApi::class)
@Serializable
private data class ExpenseEntityJson(
    val title: String,
    val category: String = "",
    val amount: Double,
    val date: String,
    val type: String
)
