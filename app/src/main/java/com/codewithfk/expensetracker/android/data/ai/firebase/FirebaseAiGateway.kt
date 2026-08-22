package com.codewithfk.expensetracker.android.data.ai.firebase

import android.util.Log
import com.codewithfk.expensetracker.android.data.ai.AiAnalysisResultData
import com.codewithfk.expensetracker.android.data.ai.AiGateway
import com.codewithfk.expensetracker.android.data.ai.model.AiAnalysisReport
import com.codewithfk.expensetracker.android.data.model.ExpenseEntity
import com.google.firebase.Firebase
import com.google.firebase.vertexai.vertexAI
import com.google.firebase.vertexai.type.generationConfig
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAiGateway @Inject constructor() : AiGateway {

    private val TAG = "FirebaseAiGateway"

    private val model = Firebase.vertexAI.generativeModel(
        modelName = "gemini-2.5-flash",
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

    override suspend fun analyzeSpending(
        history: List<ExpenseEntity>,
        startDate: String?,
        endDate: String?
    ): Result<AiAnalysisResultData> {
        Log.d(TAG, "analyzeSpending: count=${history.size}, period=$startDate ~ $endDate")
        val historyText = if (history.isEmpty()) "거래 내역이 없습니다." else history.joinToString("\n") { 
            val catPrefix = if (it.category.isNotBlank()) "[${it.category}] " else ""
            "${it.date}: $catPrefix${it.title} - ₩${it.amount} (${it.type})" 
        }
        val periodText = if (startDate != null && endDate != null) "$startDate ~ $endDate" else "전체 기간"

        val prompt = """
            Analyze the following expense history for the period ($periodText) and provide a report in JSON format.
            History:
            $historyText
            
            JSON structure:
            {
                "summary": "Short summary of overall spending in Korean",
                "insights": ["insight 1", "insight 2"],
                "savingTips": ["tip 1", "tip 2"],
                "period": "Period of analysis (e.g., $periodText)"
            }
        """.trimIndent()

        Log.d(TAG, "analyzeSpending: geminiInput=\n$prompt")

        val startTime = System.currentTimeMillis()
        return try {
            val response = model.generateContent(prompt)
            val durationMs = System.currentTimeMillis() - startTime
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
                    modelName = "gemini-2.5-flash",
                    provider = "Firebase (SaaS)",
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
