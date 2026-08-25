package com.codewithfk.expensetracker.android.ai.gateway

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.codewithfk.expensetracker.android.ai.model.AiAnalysisReport
import com.codewithfk.expensetracker.android.ai.model.AiMasterRouterResponse
import com.codewithfk.expensetracker.android.ai.model.AiSecondPassResponse
import com.codewithfk.expensetracker.android.ai.AiPromptTemplates
import com.codewithfk.expensetracker.android.data.model.ChatMessageEntity
import com.codewithfk.expensetracker.android.data.model.ExpenseEntity
import com.codewithfk.expensetracker.android.utils.Utils
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
        val periodText = if (startDate != null && endDate != null) "$startDate ~ $endDate" else "전체 기간"
        
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
        
        // 1단계: 의도 파악 시작 (생각 중... 표시)
        emit(ChatResponse.Chunk("🔍 의도를 분석하고 있습니다...\n"))

        // 대화 기록 구성 (직전 1개 대화 턴 추출)
        val historyToUse = if (messages.size >= 3) messages.dropLast(1).takeLast(2)
        else if (messages.size == 2) messages.take(1)
        else emptyList()

        val historyContext = historyToUse.joinToString("\n") { 
            "${if (it.role == ChatMessageEntity.ROLE_USER) "User" else "Assistant"}: ${it.content}" 
        }

        // 1. 마스터 라우터를 통한 인텐트 분류 수행 (문맥 포함)
        val routingInput = if (historyContext.isNotBlank()) "Context:\n$historyContext\n\nQuestion: $lastMessage" else lastMessage
        Log.d(TAG, "chatStream: [1st PASS INPUT]\n$routingInput")
        
        val routingResultData = routeIntent(routingInput, modelName).getOrElse { e ->
            Log.e(TAG, "chatStream routing error: ${e.message}")
            emit(ChatResponse.Chunk("\n❌ **오류가 발생했습니다.**\n잠시 후 다시 시도해주세요.\n(사유: ${e.message ?: "Internal Server Error"})"))
            return@flow
        }
        var routerRes = routingResultData.response
        Log.d(TAG, "chatStream: [1st PASS RESULT] intent=${routerRes.intent}, start=${routerRes.start_date}, end=${routerRes.end_date}")

        // CONTEXT_REFERENCE 처리 (맥락 해소)
        if (routerRes.intent == "CONTEXT_REFERENCE") {
            emit(ChatResponse.Chunk("🔄 이전 대화를 확인하여 요청을 해석하고 있습니다...\n"))
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
                emit(ChatResponse.Chunk("\n❌ **맥락 분석 중 오류가 발생했습니다.**\n다시 질문해주시면 감사하겠습니다."))
                return@flow
            }
        }

        // 2. 최종 인텐트 실행
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
        
        // 마지막 메시지는 현재 질문이므로 제외
        val lastMsg = messages.lastOrNull() ?: return@flow
        val previousMessages = messages.dropLast(1)
        
        // historyLimit 적용 (단, -1은 전체 전송)
        val messagesToUse = if (historyLimit > 0) {
            previousMessages.takeLast(historyLimit)
        } else {
            previousMessages
        }
        
        // ChatHistory 구성
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
            // 마지막 질문 전송
            chatSession.sendMessageStream(lastMsg.content).collect { chunk ->
                chunk.text?.let { 
                    fullText += it
                    emit(ChatResponse.Chunk(it)) 
                }
                lastResponse = chunk
            }
        } catch (e: Exception) {
            emit(ChatResponse.Chunk("\n❌ [RAW_CHAT_ERROR]: ${e.message}"))
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
                emit(ChatResponse.Chunk("📊 가계부 데이터를 조회하고 계산 중입니다...\n"))
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
                        emit(ChatResponse.Chunk("\n❌ **데이터 조회 중 서버 오류가 발생했습니다.**\n잠시 후 다시 시도해주세요."))
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
                        "총액은 **${Utils.formatCurrency(sum)}**입니다. (분석 대상: ${finalItems.size}건)"
                    }
                    "MAX" -> {
                        val maxItem = finalItems.maxByOrNull { it.amount }
                        if (maxItem != null) "가장 큰 금액은 **${maxItem.title}**의 **${Utils.formatCurrency(maxItem.amount)}**입니다."
                        else "해당하는 내역을 찾을 수 없습니다."
                    }
                    "MIN" -> {
                        val minItem = finalItems.minByOrNull { it.amount }
                        if (minItem != null) "가장 작은 금액은 **${minItem.title}**의 **${Utils.formatCurrency(minItem.amount)}**입니다."
                        else "해당하는 내역을 찾을 수 없습니다."
                    }
                    "COUNT" -> "해당하는 내역은 총 **${finalItems.size}건**입니다."
                    "LIST" -> {
                        val displayItems = finalItems.take(10)
                        val listText = displayItems.joinToString("\n") { "- ${it.date}: ${it.title} (${Utils.formatCurrency(it.amount)})" }
                        val suffix = if (finalItems.size > 10) "\n\n...외 ${finalItems.size - 10}건이 더 있습니다. [자세히 보기]" else ""
                        if (finalItems.isNotEmpty()) "요청하신 내역 리스트입니다:\n\n$listText$suffix" else "조회된 내역이 없습니다."
                    }
                    else -> "조건에 맞는 데이터를 찾았지만 연산 종류를 결정하지 못했습니다."
                }
                emit(ChatResponse.Chunk("\n**AI 답변:**\n$resultMessage\n\n> 💡 **AI 판단 근거:** $reasoning"))
                
                if (operation == "LIST") {
                    emit(ChatResponse.ShowDetails(finalItems, "조회 내역 상세"))
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
                emit(ChatResponse.Chunk("⚙️ 요청하신 작업을 정리하고 있습니다...\n"))
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
                        emit(ChatResponse.Chunk("\n❌ **작업 분석 중 서버 오류가 발생했습니다.**\n잠시 후 다시 시도해주세요."))
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
                    "DELETE" -> "삭제"
                    "UPDATE" -> "수정 (${updateField ?: "필드"} 변경)"
                    "INSERT" -> "추가"
                    else -> "처리"
                }
                
                val displayItems = targetItems.take(10)
                val targetListText = displayItems.joinToString("\n") { "- ${it.date}: ${it.title} (${Utils.formatCurrency(it.amount)})" }
                val suffix = if (targetItems.size > 10) "\n\n...외 ${targetItems.size - 10}건이 더 있습니다. [자세히 보기]" else ""

                emit(ChatResponse.Chunk("\n**AI 답변:**\n다음 항목들에 대해 **$actionText** 작업을 진행할까요? (대상: ${targetItems.size}건)\n\n$targetListText$suffix\n\n> 💡 **이유:** $reasoning"))
                
                emit(ChatResponse.ShowDetails(targetItems, "상세 내역"))

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
                emit(ChatResponse.Chunk("📝 데이터를 정밀 분석하고 제언을 작성 중입니다...\n"))
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
                    "수입: ${Utils.formatCurrency(income)} | 지출: ${Utils.formatCurrency(expense)} (총 ${items.size}건)"
                }
                val summaryTableText = monthlySummaryMap.entries.sortedBy { it.key }.joinToString("\n") { "- ${it.key}: ${it.value}" }
                val rawDataText = filteredHistory.joinToString("\n") { "${it.date} | ${it.category} | ${it.title} | ${it.amount}원" }
                val combinedDataContext = "### [Monthly Summary Table]\n$summaryTableText\n\n### [Detailed Transaction Data]\n$rawDataText"

                val startTime2 = System.nanoTime()
                val secondPassPrompt = AiPromptTemplates.getSecondPassAnalysisPrompt(lastMessage, combinedDataContext)
                emit(ChatResponse.Chunk("\n**AI 답변:**\n"))
                
                var lastResponse: GenerateContentResponse? = null
                try {
                    chatModel.generateContentStream(secondPassPrompt).collect { chunk ->
                        chunk.text?.let { emit(ChatResponse.Chunk(it)) }
                        lastResponse = chunk
                    }
                } catch (e: Exception) {
                    emit(ChatResponse.Chunk("\n❌ **데이터 분석 스트리밍 중 오류가 발생했습니다.**"))
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
                emit(ChatResponse.Chunk("🛠️ Mermaid 다이어그램 오류를 분석하여 수정 중입니다...\n"))
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
                    // JSON 제약이 없는 chatModel을 사용하여 자연스러운 마크다운 응답을 받습니다.
                    val res = chatModel.generateContent(fixPrompt)
                    secondPassUsage = res.usageMetadata
                    res.text ?: "오류를 수정하지 못했습니다."
                } catch (e: Exception) {
                    emit(ChatResponse.Chunk("\n❌ **오류 수정 중 서버 에러가 발생했습니다.**"))
                    return@handleIntent
                }

                emit(ChatResponse.Chunk("\n**수정된 결과:**\n$response"))

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
                emit(ChatResponse.Chunk("💬 답변을 작성하고 있습니다...\n"))
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
                        emit(ChatResponse.Chunk("\n❌ **답변 생성 중 오류가 발생했습니다.**"))
                        return@handleIntent
                    }
                    secondPassUsage = res.usageMetadata
                    res.text ?: "죄송합니다. 이해하지 못했습니다."
                }
                emit(ChatResponse.Chunk("\n**AI 답변:**\n$response"))
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
                emit(ChatResponse.Chunk("📊 데이터를 시각화하여 다이어그램으로 정리 중입니다...\n"))
                val filteredHistory = filterHistory(history, routerRes)
                val rawDataText = filteredHistory.joinToString("\n") { "${it.date} | ${it.category} | ${it.title} | ${it.amount}원" }
                
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
                    res.text ?: "시각화에 실패했습니다."
                } catch (e: Exception) {
                    emit(ChatResponse.Chunk("\n❌ **시각화 생성 중 오류가 발생했습니다.**"))
                    return@handleIntent
                }

                emit(ChatResponse.Chunk("\n**시각화 결과:**\n$response"))

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
                emit(ChatResponse.Chunk("🚀 **앱 기능을 실행합니다:** `$action`"))
                
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
                emit(ChatResponse.Chunk("❓ **정의되지 않은 인텐트입니다.**"))
            }
        }
    }

    private fun todayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
    }

    private fun filterHistory(history: List<ExpenseEntity>, routerRes: AiMasterRouterResponse): List<ExpenseEntity> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val historyDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        
        // AI가 날짜를 잘못 넘겼을 경우를 대비해 try-catch로 안전하게 처리
        val start = routerRes.start_date?.let { try { dateFormat.parse(it) } catch(e: Exception) { null } }
        val end = routerRes.end_date?.let { try { dateFormat.parse(it) } catch(e: Exception) { null } }
        
        val filtered = history.filter { item ->
            // 날짜 문자열 공백 제거 및 유연한 파싱
            val dateStr = item.date.trim()
            val itemDate = try { 
                historyDateFormat.parse(dateStr) 
            } catch (e: Exception) { 
                // 형식이 다를 경우(예: 단일 숫자 d/M/yyyy) 대비
                try { 
                    SimpleDateFormat("d/M/yyyy", Locale.US).parse(dateStr) 
                } catch(_: Exception) {
                    Log.e(TAG, "filterHistory: Fatal parsing error for date '$dateStr'")
                    null 
                }
            }
            
            // 기간 필터링: 날짜가 하나라도 없으면(AI가 기간 지정을 안 했으면) 전체 포함
            val dateMatch = if (start != null && end != null && itemDate != null) {
                !itemDate.before(start) && !itemDate.after(end)
            } else true
            
            // 카테고리 필터링: 카테고리 리스트가 비어있으면 전체 포함
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
            val suffix = if (month == currentMonthKey) " (이번 달)" else ""
            "- $month (1일~${currentDay}일): ₩${String.format(Locale.getDefault(), "%,.0f", amount)}$suffix"
        }
        
        cal.time = Calendar.getInstance().time
        cal.add(Calendar.MONTH, -1)
        val lastMonthKey = monthFormat.format(cal.time)
        val currentMonthAmount = monthlyComparisonMap[currentMonthKey] ?: 0.0
        val lastMonthAmount = monthlyComparisonMap[lastMonthKey] ?: 0.0
        val summaryStatus = if (lastMonthAmount > 0) {
            val diff = currentMonthAmount - lastMonthAmount
            val percent = (diff / lastMonthAmount) * 100
            if (diff > 0) "지난 달 동기 대비 ${String.format(Locale.getDefault(), "%.1f", percent)}% 증가"
            else "지난 달 동기 대비 ${String.format(Locale.getDefault(), "%.1f", abs(percent))}% 감소"
        } else "비교 데이터 부족"

        val comparisonText = "[기준: 매월 1일 ~ ${currentDay}일 누적 지출]\n$comparisonListText\n* 요약: $summaryStatus"

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
            yearlySummary.entries.sortedBy { it.key }.joinToString("\n") { "- ${it.key}년: 월 평균 지출 ₩${String.format(Locale.getDefault(), "%,.0f", it.value.third)} (총 ₩${String.format(Locale.getDefault(), "%,.0f", it.value.first)} / ${it.value.second}개월)" }
        } else ""

        val monthlyText = monthlySummary.entries.sortedBy { it.key }.joinToString("\n") {
            val (income, expense) = it.value
            val incomeStr = if (income > 0) "총 수입 ₩${String.format(Locale.getDefault(), "%,.0f", income)} / " else ""
            "- ${it.key}: ${incomeStr}총 지출 ₩${String.format(Locale.getDefault(), "%,.0f", expense)}"
        }

        val categoryText = categorySummary.entries.sortedByDescending { it.value.second }.joinToString("\n") {
            "- ${it.key}: ₩${String.format(Locale.getDefault(), "%,.0f", it.value.first)} (${String.format(Locale.getDefault(), "%.1f", it.value.second)}%)"
        }

        val top10Text = if (top10Expenses.isEmpty()) "고액 지출 내역이 없습니다." else top10Expenses.withIndex().joinToString("\n") { (index, it) ->
            val parts = it.date.split("/")
            val isoDate = if (parts.size == 3) "${parts[2]}-${parts[1]}-${parts[0]}" else it.date
            "${index + 1}. date: $isoDate | category: ${it.category.ifBlank { "미지정" }} | title: ${it.title} | amount: ${String.format(Locale.getDefault(), "%,.0f", it.amount)} ₩"
        }

        val recentHistoryText = if (recentHistory.isEmpty()) "최근 30일간 거래 내역이 없습니다." else recentHistory.withIndex().joinToString("\n") { (index, it) ->
            val parts = it.date.split("/")
            val isoDate = if (parts.size == 3) "${parts[2]}-${parts[1]}-${parts[0]}" else it.date
            "${index + 1}. date: $isoDate | category: ${it.category.ifBlank { "미지정" }} | title: ${it.title} | type: ${it.type} | amount: ${String.format(Locale.getDefault(), "%,.0f", it.amount)} ₩"
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
