package com.codewithfk.expensetracker.android.ai.core

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.codewithfk.expensetracker.android.ai.core.model.AiAnalysisReport
import com.codewithfk.expensetracker.android.ai.core.model.AiMasterRouterResponse
import com.codewithfk.expensetracker.android.ai.core.prompt.AiPromptTemplates
import com.codewithfk.expensetracker.android.data.model.ChatMessageEntity
import com.codewithfk.expensetracker.android.data.model.ExpenseEntity
import com.codewithfk.expensetracker.android.utils.Utils
import com.google.firebase.Firebase
import com.google.firebase.vertexai.type.content
import com.google.firebase.vertexai.vertexAI
import com.google.firebase.vertexai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAiGateway @Inject constructor() : AiGateway {
    companion object {
        val TAG = "FirebaseAiGateway"
        val modelName: String = "gemini-2.5-flash-lite"
        val smartModelName: String = "gemini-2.5-flash"
        val provider: String = "Firebase"
        val agentVersion: String = "v2.2"
        val useDetailedAnalysis: Boolean = false
    }

    private val model = Firebase.vertexAI.generativeModel(
        modelName = modelName,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
        }
    )
    private val smartModel = Firebase.vertexAI.generativeModel(
        modelName = smartModelName,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
        }
    )

    private val chatModel = Firebase.vertexAI.generativeModel(
        modelName = modelName
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
            val response = smartModel.generateContent(prompt)
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

        val targetModelName = modelName ?: smartModelName
        val currentModel = if (targetModelName == smartModelName) smartModel 
        else Firebase.vertexAI.generativeModel(
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
                    modelName = targetModelName
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
            val response = model.generateContent(prompt)
            val durationMs = (System.nanoTime() - startTime) / 1_000_000
            
            val responseText = response.text ?: throw Exception("Empty response from AI")
            val usage = response.usageMetadata
            val promptTokens = usage?.promptTokenCount
            val candidatesTokens = usage?.candidatesTokenCount
            val totalTokens = usage?.totalTokenCount
            
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
                    modelName = FirebaseAiGateway.modelName,
                    provider = FirebaseAiGateway.provider,
                    rawResponseJson = responseText
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
        
        val routingResultData = routeIntent(routingInput, modelName).getOrNull() ?: return@flow
        val routerRes = routingResultData.response
        Log.d(TAG, "chatStream: [1st PASS RESULT] intent=${routerRes.intent}, start=${routerRes.start_date}, end=${routerRes.end_date}, categories=${routerRes.sub_categories}")

        // 1차 분석 결과 JSON 출력
        val firstPassJson = json.encodeToString(AiMasterRouterResponse.serializer(), routerRes)
        emit(ChatResponse.Chunk("### 1st Pass: Intent Routing\n```json\n$firstPassJson\n```\n\n---\n\n"))

        // 2. 인텐트별 2차 패스 로직 분기
        when (routerRes.intent) {
            "DATA_RETRIEVAL" -> {
                val filteredHistory = filterHistory(history, routerRes)
                val uniqueNames = filteredHistory.map { it.title }.distinct()
                Log.d(TAG, "chatStream: [DATA_RETRIEVAL] Filtered Items: ${filteredHistory.size}, Unique Names: ${uniqueNames.size}")
                
                val startTime2 = System.nanoTime()
                val secondPassPrompt = AiPromptTemplates.getSecondPassRetrievalPrompt(lastMessage, uniqueNames)
                Log.d(TAG, "chatStream: [2nd PASS RETRIEVAL PROMPT]\n$secondPassPrompt")

                val secondPassRes = smartModel.generateContent(secondPassPrompt)
                val durationMs2 = (System.nanoTime() - startTime2) / 1_000_000
                
                val secondPassText = secondPassRes.text ?: ""
                val usage2 = secondPassRes.usageMetadata
                Log.d(TAG, "chatStream: [2nd PASS RESULT]\n$secondPassText")
                
                // [계산 로직 시작]
                val retrievalResult = try {
                    json.decodeFromString<com.codewithfk.expensetracker.android.ai.core.model.AiSecondPassResponse>(secondPassText)
                } catch (e: Exception) { null }

                if (retrievalResult != null) {
                    val finalItems = filteredHistory.filter { it.title in retrievalResult.relevant_names }
                    val resultMessage = when (retrievalResult.operation) {
                        "SUM" -> {
                            val sum = finalItems.sumOf { it.amount }
                            "총액은 **${Utils.formatCurrency(sum)}**입니다. (대상 항목: ${retrievalResult.relevant_names.joinToString(", ")})"
                        }
                        "MAX" -> {
                            val maxItem = finalItems.maxByOrNull { it.amount }
                            "가장 큰 금액은 **${maxItem?.title ?: "없음"}**의 **${Utils.formatCurrency(maxItem?.amount ?: 0.0)}**입니다."
                        }
                        "MIN" -> {
                            val minItem = finalItems.minByOrNull { it.amount }
                            "가장 작은 금액은 **${minItem?.title ?: "없음"}**의 **${Utils.formatCurrency(minItem?.amount ?: 0.0)}**입니다."
                        }
                        "COUNT" -> "해당하는 내역은 총 **${finalItems.size}건**입니다."
                        "LIST" -> {
                            val listText = finalItems.joinToString("\n") { "- ${it.date}: ${it.title} (${Utils.formatCurrency(it.amount)})" }
                            "요청하신 내역 리스트입니다:\n\n$listText"
                        }
                        else -> "조건에 맞는 데이터를 찾았지만 연산 종류를 결정하지 못했습니다."
                    }
                    emit(ChatResponse.Chunk("$resultMessage\n\n> 💡 **AI 판단 근거:** ${retrievalResult.reasoning}"))
                } else {
                    emit(ChatResponse.Chunk("데이터를 분석하는 과정에서 오류가 발생했습니다.\n\nRaw JSON: $secondPassText"))
                }

                // 1차 + 2차 메타데이터 합산
                emit(ChatResponse.Metadata(
                    promptTokens = (routingResultData.promptTokens ?: 0) + (usage2?.promptTokenCount ?: 0),
                    candidatesTokens = (routingResultData.candidatesTokens ?: 0) + (usage2?.candidatesTokenCount ?: 0),
                    totalTokens = (routingResultData.totalTokens ?: 0) + (usage2?.totalTokenCount ?: 0),
                    responseTimeMs = routingResultData.responseTimeMs + durationMs2,
                    modelName = routingResultData.modelName,
                    agentVersion = agentVersion,
                    provider = provider
                ))
            }

            "DATA_MANIPULATION" -> {
                val filteredHistory = filterHistory(history, routerRes)
                val uniqueNames = filteredHistory.map { it.title }.distinct()
                Log.d(TAG, "chatStream: [DATA_MANIPULATION] Filtered Items: ${filteredHistory.size}")
                
                val startTime2 = System.nanoTime()
                val secondPassPrompt = AiPromptTemplates.getSecondPassManipulationPrompt(lastMessage, uniqueNames)
                Log.d(TAG, "chatStream: [2nd PASS MANIPULATION PROMPT]\n$secondPassPrompt")

                val secondPassRes = smartModel.generateContent(secondPassPrompt)
                val durationMs2 = (System.nanoTime() - startTime2) / 1_000_000
                
                val secondPassText = secondPassRes.text ?: ""
                val usage2 = secondPassRes.usageMetadata
                Log.d(TAG, "chatStream: [2nd PASS RESULT]\n$secondPassText")
                
                // [조작 대상 추출 로직]
                val manipulationResult = try {
                    json.decodeFromString<com.codewithfk.expensetracker.android.ai.core.model.AiSecondPassResponse>(secondPassText)
                } catch (e: Exception) { null }

                if (manipulationResult != null) {
                    val actionText = when (manipulationResult.action) {
                        "DELETE" -> "삭제"
                        "UPDATE" -> "수정 (${manipulationResult.update_field} 변경)"
                        "INSERT" -> "추가"
                        else -> "처리"
                    }
                    
                    val targetItems = filteredHistory.filter { it.title in manipulationResult.relevant_names }
                    val targetListText = targetItems.joinToString("\n") { "- ${it.date}: ${it.title} (${Utils.formatCurrency(it.amount)})" }
                    
                    emit(ChatResponse.Chunk("다음 항목들에 대해 **$actionText** 작업을 진행할까요?\n\n$targetListText\n\n> 💡 **이유:** ${manipulationResult.reasoning}"))
                    
                    // UI에 컨펌 다이얼로그를 띄우기 위한 액션 요청 전송
                    emit(ChatResponse.ActionRequest(
                        intent = routerRes.intent,
                        action = manipulationResult.action ?: "DELETE",
                        targetItems = targetItems,
                        reasoning = manipulationResult.reasoning ?: "",
                        updateField = manipulationResult.update_field
                    ))
                } else {
                    emit(ChatResponse.Chunk("조작 대상을 분석하는 과정에서 오류가 발생했습니다.\n\nRaw JSON: $secondPassText"))
                }

                // 1차 + 2차 메타데이터 합산
                emit(ChatResponse.Metadata(
                    promptTokens = (routingResultData.promptTokens ?: 0) + (usage2?.promptTokenCount ?: 0),
                    candidatesTokens = (routingResultData.candidatesTokens ?: 0) + (usage2?.candidatesTokenCount ?: 0),
                    totalTokens = (routingResultData.totalTokens ?: 0) + (usage2?.totalTokenCount ?: 0),
                    responseTimeMs = routingResultData.responseTimeMs + durationMs2,
                    modelName = routingResultData.modelName,
                    agentVersion = agentVersion,
                    provider = provider
                ))
            }
            
            "DATA_ANALYSIS" -> {
                // 앱 처리: 기간 내 실제 지출 데이터 추출
                val filteredHistory = filterHistory(history, routerRes)
                val rawDataText = filteredHistory.joinToString("\n") { 
                    "${it.date} | ${it.category} | ${it.title} | ${it.amount}원" 
                }
                Log.d(TAG, "chatStream: [DATA_ANALYSIS] Filtered Items: ${filteredHistory.size}")
                
                val startTime2 = System.nanoTime()
                val secondPassPrompt = AiPromptTemplates.getSecondPassAnalysisPrompt(lastMessage, rawDataText)
                Log.d(TAG, "chatStream: [2nd PASS ANALYSIS PROMPT]\n$secondPassPrompt")

                emit(ChatResponse.Chunk("### 2nd Pass: Data Analysis\n"))
                
                var lastResponse: com.google.firebase.vertexai.type.GenerateContentResponse? = null
                chatModel.generateContentStream(secondPassPrompt).collect { chunk ->
                    chunk.text?.let { emit(ChatResponse.Chunk(it)) }
                    lastResponse = chunk
                }
                
                val durationMs2 = (System.nanoTime() - startTime2) / 1_000_000
                val usage2 = lastResponse?.usageMetadata
                
                // 1차 + 2차 메타데이터 합산
                emit(ChatResponse.Metadata(
                    promptTokens = (routingResultData.promptTokens ?: 0) + (usage2?.promptTokenCount ?: 0),
                    candidatesTokens = (routingResultData.candidatesTokens ?: 0) + (usage2?.candidatesTokenCount ?: 0),
                    totalTokens = (routingResultData.totalTokens ?: 0) + (usage2?.totalTokenCount ?: 0),
                    responseTimeMs = routingResultData.responseTimeMs + durationMs2,
                    modelName = routingResultData.modelName,
                    agentVersion = agentVersion,
                    provider = provider
                ))
            }
            
            "SIMPLE_RESPONSE", "FALLBACK" -> {
                val startTime2 = System.nanoTime()
                var secondPassUsage: com.google.firebase.vertexai.type.UsageMetadata? = null
                
                val response = routerRes.reply_message ?: run {
                    val historyText = getSummarizedHistoryParts(history).toFullMarkdown()
                    val systemPrompt = AiPromptTemplates.getChatPrompt(historyText, todayDate())
                    val prompt = "$systemPrompt\n\nUser Question: $lastMessage"
                    val res = chatModel.generateContent(prompt)
                    secondPassUsage = res.usageMetadata
                    res.text ?: "죄송합니다. 이해하지 못했습니다."
                }
                
                val durationMs2 = (System.nanoTime() - startTime2) / 1_000_000
                emit(ChatResponse.Chunk(response))
                
                // 1차 + 2차 메타데이터 합산
                emit(ChatResponse.Metadata(
                    promptTokens = (routingResultData.promptTokens ?: 0) + (secondPassUsage?.promptTokenCount ?: 0),
                    candidatesTokens = (routingResultData.candidatesTokens ?: 0) + (secondPassUsage?.candidatesTokenCount ?: 0),
                    totalTokens = (routingResultData.totalTokens ?: 0) + (secondPassUsage?.totalTokenCount ?: 0),
                    responseTimeMs = routingResultData.responseTimeMs + durationMs2,
                    modelName = routingResultData.modelName,
                    agentVersion = agentVersion,
                    provider = provider
                ))
            }
            
            "APP_ACTION" -> {
                emit(ChatResponse.Chunk("앱 기능을 실행합니다: ${routerRes.sub_categories.joinToString(", ")}"))
            }

            "CONTEXT_REFERENCE" -> {
                emit(ChatResponse.Chunk("이전 대화 맥락을 참조하여 요청을 처리 중입니다..."))
                // 여기서 필요하다면 1차 routing 결과를 바탕으로 이전 인텐트를 재해석하거나 수정 로직을 돌릴 수 있음
            }
            
            else -> {
                emit(ChatResponse.Chunk("정의되지 않은 인텐트입니다."))
            }
        }
    }

    private fun todayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
    }

    private fun filterHistory(history: List<ExpenseEntity>, routerRes: AiMasterRouterResponse): List<ExpenseEntity> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val historyDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        
        val start = routerRes.start_date?.let { try { dateFormat.parse(it) } catch(e: Exception) { null } }
        val end = routerRes.end_date?.let { try { dateFormat.parse(it) } catch(e: Exception) { null } }
        
        val filtered = history.filter { item ->
            val itemDate = try { 
                historyDateFormat.parse(item.date.trim()) 
            } catch (e: Exception) { 
                Log.e(TAG, "filterHistory: Parsing error for item '${item.title}' with date '${item.date}'")
                null 
            }
            
            val dateMatch = if (start != null && end != null && itemDate != null) {
                !itemDate.before(start) && !itemDate.after(end)
            } else true
            
            val categoryMatch = if (routerRes.sub_categories.isNotEmpty()) {
                routerRes.sub_categories.contains(item.category)
            } else true
            
            dateMatch && categoryMatch
        }
        
        Log.d(TAG, "filterHistory: [FILTER] Range: $start ~ $end, Categories: ${routerRes.sub_categories}, Result Count: ${filtered.size}")
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
            else "지난 달 동기 대비 ${String.format(Locale.getDefault(), "%.1f", kotlin.math.abs(percent))}% 감소"
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
