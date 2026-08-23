package com.codewithfk.expensetracker.android.data.ai.firebase

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.codewithfk.expensetracker.android.data.ai.AiAnalysisResultData
import com.codewithfk.expensetracker.android.data.ai.AiGateway
import com.codewithfk.expensetracker.android.data.ai.model.AiAnalysisReport
import com.codewithfk.expensetracker.android.data.ai.prompt.AiPromptTemplates
import com.codewithfk.expensetracker.android.data.model.ExpenseEntity
import com.codewithfk.expensetracker.android.utils.Utils
import com.google.firebase.Firebase
import com.google.firebase.vertexai.vertexAI
import com.google.firebase.vertexai.type.generationConfig
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

    private val TAG = "FirebaseAiGateway"
    companion object {
        // 사용 가능한 모델 2026.08.23 기준
        // pro > flash > lite로 갈수록 저렴한 토큰, 빠른 속도, 낮은 정확도
        // "gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.5-flash-lite"
        val modelName: String = "gemini-2.5-flash-lite"
        val smartModelName: String = "gemini-2.5-flash"
        val provider: String = "Firebase (SaaS)"
        /**
            v1 - 모든 거래 내역 원본 전송 (Baseline)
            v2 - 데이터 전처리 도입 (월별 입출금 합계, 카테고리별 비중 계산)
            v2.1 - 프롬프트 최적화 (단순 수치 나열 지양, 소비 트렌드 분석 강화)
            v2.2 - 시계열 비교 분석 (월별 동일 기간 누적 지출액 추이 비교)
        */
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

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun parseExpense(input: String, isIncome: Boolean): Result<ExpenseEntity> {
        Log.d(TAG, "parseExpense: input=$input, isIncome=$isIncome")
        val now = Calendar.getInstance()
        val categories = if (isIncome) Utils.incomeCategories else Utils.expenseCategories
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
        
        // AI가 참고할 최근 14일치 달력 생성
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

        Log.d(TAG, "parseExpense: geminiInput=\n$prompt")

        return try {
            val response = smartModel.generateContent(prompt)
            val responseText = response.text ?: throw Exception("Empty response from AI")
            Log.d(TAG, "parseExpense: response=$responseText")
            
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

        // 1. Data Preprocessing (Kotlin side)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        val monthFormat = SimpleDateFormat("yyyy-MM", Locale.US)
        val thirtyDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.time

        // A. Monthly Summary (Income/Expense)
        val monthlySummary = history.groupBy {
            try {
                monthFormat.format(dateFormat.parse(it.date.trim())!!)
            } catch (_: Exception) {
                "Unknown"
            }
        }.mapValues { (_, items) ->
            val income = items.filter { it.type.trim().equals("Income", ignoreCase = true) }.sumOf { it.amount }
            val expense = items.filter { it.type.trim().equals("Expense", ignoreCase = true) }.sumOf { it.amount }
            Pair(income, expense)
        }

        // B. Category Analysis (Expense Only)
        val expensesOnly = history.filter { it.type.trim().equals("Expense", ignoreCase = true) }
        val totalExpenseAmount = expensesOnly.sumOf { it.amount }
        val categorySummary = expensesOnly.groupBy { 
            it.category.ifBlank { it.title }.trim() 
        }.mapValues { (_, items) ->
            val amount = items.sumOf { it.amount }
            val percentage = if (totalExpenseAmount > 0) (amount / totalExpenseAmount) * 100 else 0.0
            Pair(amount, percentage)
        }

        // C. Top 10 High-value Spending (Outliers)
        val top10Expenses = expensesOnly.sortedByDescending { it.amount }.take(10)

        // D. Recent 30 Days Detailed History
        val recentHistory = history.filter {
            try {
                val date = dateFormat.parse(it.date.trim())
                date != null && date.after(thirtyDaysAgo)
            } catch (_: Exception) {
                false
            }
        }

        // E. Month-over-Month Comparison (1st to Current Day for each month)
        val cal = Calendar.getInstance()
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val currentMonthKey = monthFormat.format(cal.time)
        
        val monthlyComparisonMap = history.filter { 
            it.type.trim().equals("Expense", ignoreCase = true) 
        }.groupBy {
            try { monthFormat.format(dateFormat.parse(it.date.trim())!!) } catch (_: Exception) { "Unknown" }
        }.filter { it.key != "Unknown" }
        .mapValues { (_, items) ->
            // 각 월의 1일부터 현재 일(currentDay)까지의 지출만 합산
            val sumSoFar = items.filter {
                try {
                    val itemDate = dateFormat.parse(it.date.trim())!!
                    val itemCal = Calendar.getInstance().apply { time = itemDate }
                    itemCal.get(Calendar.DAY_OF_MONTH) <= currentDay
                } catch (_: Exception) { false }
            }.sumOf { it.amount }
            sumSoFar
        }

        val sortedComparison = monthlyComparisonMap.entries.sortedBy { it.key }
        val comparisonListText = sortedComparison.joinToString("\n") { (month, amount) ->
            val suffix = if (month == currentMonthKey) " (이번 달)" else ""
            "- $month (1일~${currentDay}일): ₩${String.format(Locale.getDefault(), "%,.0f", amount)}$suffix"
        }

        // 이번 달 vs 지난 달 증감률 계산 (요약용)
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

        val comparisonText = """
            [기준: 매월 1일 ~ ${currentDay}일 누적 지출]
            $comparisonListText
            
            * 요약: $summaryStatus
        """.trimIndent()

        // F. Yearly Trend Analysis (Yearly Monthly Average)
        val yearFormat = SimpleDateFormat("yyyy", Locale.US)
        val yearlySummary = expensesOnly.groupBy {
            try {
                yearFormat.format(dateFormat.parse(it.date.trim())!!)
            } catch (_: Exception) {
                "Unknown"
            }
        }.filter { it.key != "Unknown" }
        .mapValues { (_, items) ->
            val totalYearExpense = items.sumOf { it.amount }
            val activeMonths = items.map { 
                try { monthFormat.format(dateFormat.parse(it.date.trim())!!) } catch (_: Exception) { "" }
            }.distinct().filter { it.isNotBlank() }.size
            
            val monthlyAverage = if (activeMonths > 0) totalYearExpense / activeMonths else 0.0
            Triple(totalYearExpense, activeMonths, monthlyAverage)
        }

        val yearlyTrendText = if (yearlySummary.size >= 2) {
            yearlySummary.entries.sortedBy { it.key }.joinToString("\n") {
                "- ${it.key}년: 월 평균 지출 ₩${String.format(Locale.getDefault(), "%,.0f", it.value.third)} (총 ₩${String.format(Locale.getDefault(), "%,.0f", it.value.first)} / ${it.value.second}개월)"
            }
        } else {
            ""
        }

        // 2. Structure Markdown Prompt
        val periodText = if (startDate != null && endDate != null) "$startDate ~ $endDate" else "전체 기간"
        
        val monthlyText = monthlySummary.entries.sortedBy { it.key }.joinToString("\n") {
            val income = it.value.first
            val expense = it.value.second
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

        val prompt = if (useDetailedAnalysis) {
            AiPromptTemplates.getAnalyzeDetailSpendingPrompt(
                periodText = periodText,
                monthlyText = monthlyText,
                categoryText = categoryText,
                top10Text = top10Text,
                recentHistoryText = recentHistoryText,
                comparisonText = comparisonText,
                yearlyTrendText = yearlyTrendText
            )
        } else {
            AiPromptTemplates.getAnalyzeSpendingPrompt(
                periodText = periodText,
                monthlyText = monthlyText,
                categoryText = categoryText,
                top10Text = top10Text,
                recentHistoryText = recentHistoryText,
                comparisonText = comparisonText,
                yearlyTrendText = yearlyTrendText
            )
        }

        Log.d(TAG, "analyzeSpending: geminiInput=\n$prompt")

        return try {
            val startTime = System.nanoTime()
            val response = model.generateContent(prompt)
            val durationMs = (System.nanoTime() - startTime) / 1_000_000
            
            val responseText = response.text ?: throw Exception("Empty response from AI")
            val usage = response.usageMetadata
            val promptTokens = usage?.promptTokenCount
            val candidatesTokens = usage?.candidatesTokenCount
            val totalTokens = usage?.totalTokenCount
            Log.d(
                TAG,
                "analyzeSpending: response=$responseText (took ${durationMs}ms, tokens: prompt=$promptTokens, candidates=$candidatesTokens, total=$totalTokens)"
            )
            val decodedReport = json.decodeFromString<AiAnalysisReport>(responseText)

            // 상세 분석 모드인 경우 기존 UI/DB 호환을 위해 필드 매핑
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
