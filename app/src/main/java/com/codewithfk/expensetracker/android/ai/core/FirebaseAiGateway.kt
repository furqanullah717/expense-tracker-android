package com.codewithfk.expensetracker.android.ai.core

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.codewithfk.expensetracker.android.ai.core.model.AiAnalysisReport
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

    private val json = Json { ignoreUnknownKeys = true }

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
        val startTime = System.nanoTime()

        // 사용자의 금융 데이터 요약본
        val historyText = getSummarizedHistoryParts(history).toFullMarkdown()

        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)

        val systemPrompt = AiPromptTemplates.getChatPrompt(historyText, todayDate)

        // 슬라이딩 윈도우 적용 (사용자 선택 리밋에 따라 기억 용량 결정)
        val actualLimit = if (historyLimit <= 0) messages.size else historyLimit
        val windowMessages = messages.takeLast(actualLimit + 1)
        val historyToUse = windowMessages.dropLast(1)

        val chatHistory = historyToUse.map { msg ->
            content(role = if (msg.role == ChatMessageEntity.ROLE_USER) "user" else "model") {
                text(msg.content)
            }
        }

        // Use requested model or default to modelName
        val targetModelName = modelName ?: FirebaseAiGateway.modelName
        val currentChatModel = Firebase.vertexAI.generativeModel(modelName = targetModelName)
        
        val chat = currentChatModel.startChat(history = chatHistory)

        val lastMessage = messages.lastOrNull()?.content ?: ""
        
        // 대화가 잘렸거나 첫 대화인 경우, AI가 맥락을 잊지 않도록 지시사항(systemPrompt)을 다시 포함
        val isTruncated = messages.size > (historyLimit + 1)
        val fullLastMessage = if (messages.size <= 1 || isTruncated) {
            "$systemPrompt\n\nUser Question: $lastMessage"
        } else {
            lastMessage
        }
        
        Log.d(TAG, "chatStream: [INPUT TO AI] (Model: $targetModelName, History: ${historyToUse.size}, Truncated: $isTruncated)\n$fullLastMessage")

        try {
            var lastResponse: com.google.firebase.vertexai.type.GenerateContentResponse? = null
            val rawResponse = StringBuilder()
            val prompt = content {
                images.forEach { inlineData(it.bytes, it.mimeType) }
                files.forEach { inlineData(it.bytes, it.mimeType) }
                text(fullLastMessage)
            }
            chat.sendMessageStream(prompt).collect { chunk ->
                chunk.text?.let {
                    rawResponse.append(it)
                    emit(ChatResponse.Chunk(it))
                }
                lastResponse = chunk
            }
            Log.d(TAG, "chatStream: [RAW GEMINI RESPONSE]\n$rawResponse")
            
            val durationMs = (System.nanoTime() - startTime) / 1_000_000
            val usage = lastResponse?.usageMetadata
            
            emit(ChatResponse.Metadata(
                promptTokens = usage?.promptTokenCount,
                candidatesTokens = usage?.candidatesTokenCount,
                totalTokens = usage?.totalTokenCount,
                responseTimeMs = durationMs,
                modelName = targetModelName,
                agentVersion = "v1",
                provider = provider
            ))
            
        } catch (e: Exception) {
            Log.e(TAG, "chatStream error: ${e.message}", e)
            emit(ChatResponse.Chunk("에러가 발생했습니다: ${e.message}"))
        }
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
