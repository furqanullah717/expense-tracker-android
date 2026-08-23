package com.codewithfk.expensetracker.android.data.ai.firebase

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.codewithfk.expensetracker.android.data.ai.AiAnalysisResultData
import com.codewithfk.expensetracker.android.data.ai.AiGateway
import com.codewithfk.expensetracker.android.data.ai.model.AiAnalysisReport
import com.codewithfk.expensetracker.android.data.model.ExpenseEntity
import com.google.firebase.Firebase
import com.google.firebase.vertexai.vertexAI
import com.google.firebase.vertexai.type.generationConfig
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
        val provider: String = "Firebase (SaaS)"
        /**
            v1 - 모든 거래 내역 원본 전송 (Baseline)
            v2 - 데이터 전처리 도입 (월별 입출금 합계, 카테고리별 비중 계산)
            v2.1 - 프롬프트 최적화 (단순 수치 나열 지양, 소비 트렌드 분석 강화)
            v2.2 - 시계열 비교 분석 (월별 동일 기간 누적 지출액 추이 비교)
        */
        val agentVersion: String = "v2.2"
    }

    private val model = Firebase.vertexAI.generativeModel(

        modelName = modelName,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
        }
    )

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun parseExpense(input: String): Result<ExpenseEntity> {
        Log.d(TAG, "parseExpense: input=$input")
        val prompt = """
            Extract expense details from the following Korean text and return as JSON.
            Text: "$input"
            
            JSON structure:
            {
                "title": "category name (e.g., 식비, 교통비, 쇼핑)",
                "amount": numeric_value,
                "date": "dd/MM/yyyy (use today's date if not specified: 22/08/2026)",
                "type": "Expense"
            }
        """.trimIndent()

        Log.d(TAG, "parseExpense: geminiInput=\n$prompt")

        return try {
            val response = model.generateContent(prompt)
            val responseText = response.text ?: throw Exception("Empty response from AI")
            Log.d(TAG, "parseExpense: response=$responseText")
            val entity = json.decodeFromString<ExpenseEntityJson>(responseText)
            Result.success(
                ExpenseEntity(
                    id = null,
                    title = entity.title,
                    amount = entity.amount,
                    date = entity.date,
                    type = entity.type
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
            else "지난 달 동기 대비 ${String.format(Locale.getDefault(), "%.1f", Math.abs(percent))}% 감소"
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

        val prompt = """
             Analyze the following formatted expense data for the period ($periodText) and provide a report in JSON format.
             Be concise and provide specific financial advice in Korean.
             
             [CRITICAL RULES]
            1. Focus on Spending Pace: In "Section 5", compare the "이번 달 현재까지 총 지출" with the "지난 달 동일 기간 지출". Warn the user if they are spending faster than last month.
            2. Yearly Context: If "Section 6" is provided, compare the yearly "월 평균 지출" (Monthly Averages) to identify long-term inflation or lifestyle changes. Mention if current spending is significantly higher or lower than the historical monthly average.
            3. Grounding: You MUST base your analysis EXACTLY on the item names provided in "Section 3. High-Value Spending".
            4. Focus on Trends: Focus purely on the categories and item names.
            5. Output Format: Return ONLY a valid JSON object without markdown formatting.
    
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
            
            ${if (yearlyTrendText.isNotBlank()) "## 6. Yearly Trend Analysis (Monthly Averages)\n$yearlyTrendText" else ""}
            
            JSON structure:
            {
                "summary": "Short summary of overall spending in Korean",
                "insights": ["insight 1", "insight 2", "insight 3", "insight 4"], // max 5
                "savingTips": ["tip 1", "tip 2"],  // max 3
                "period": "Period of analysis (e.g., $periodText)"
            }
        """.trimIndent()

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
            val report = json.decodeFromString<AiAnalysisReport>(responseText)
            Result.success(
                AiAnalysisResultData(
                    report = report,
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

    @kotlinx.serialization.Serializable
    private data class ExpenseEntityJson(
        val title: String,
        val amount: Double,
        val date: String,
        val type: String
    )
}
