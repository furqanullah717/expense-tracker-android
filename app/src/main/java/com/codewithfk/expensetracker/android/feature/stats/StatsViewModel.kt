package com.codewithfk.expensetracker.android.feature.stats

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.codewithfk.expensetracker.android.base.BaseViewModel
import com.codewithfk.expensetracker.android.base.UiEvent
import com.codewithfk.expensetracker.android.data.ai.AiGateway
import com.codewithfk.expensetracker.android.data.ai.model.AiAnalysisReport
import com.codewithfk.expensetracker.android.data.dao.AiAnalysisDao
import com.codewithfk.expensetracker.android.data.dao.ExpenseDao
import com.codewithfk.expensetracker.android.data.model.AiAnalysisEntity
import com.codewithfk.expensetracker.android.data.model.ExpenseEntity
import com.codewithfk.expensetracker.android.data.model.ExpenseSummary
import com.codewithfk.expensetracker.android.data.repository.AiHistoryRepository
import com.codewithfk.expensetracker.android.utils.Utils
import com.github.mikephil.charting.data.Entry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val dao: ExpenseDao,
    private val aiAnalysisDao: AiAnalysisDao,
    private val aiHistoryRepository: AiHistoryRepository,
    private val aiGateway: AiGateway
) : BaseViewModel() {
    val entries = dao.getAllExpenseByDate()
    val topEntries = dao.getTopExpenses()

    val currentUserId: String
        get() = aiHistoryRepository.getCurrentUserId()

    val aiHistoryList = (if (currentUserId.isNotBlank() && currentUserId != "anonymous") {
        aiAnalysisDao.getReportsForUser(currentUserId)
    } else {
        aiAnalysisDao.getAllReports()
    }).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _aiReport = MutableStateFlow<AiAnalysisReport?>(null)
    val aiReport: StateFlow<AiAnalysisReport?> = _aiReport

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _lastSavedEntity = MutableStateFlow<AiAnalysisEntity?>(null)
    val lastSavedEntity: StateFlow<AiAnalysisEntity?> = _lastSavedEntity

    init {
        // Sync previously saved AI reports from Firestore so they persist across reinstallations
        viewModelScope.launch {
            aiHistoryRepository.syncFromFirestore()
        }
    }

    fun syncHistoryFromCloud() {
        viewModelScope.launch {
            aiHistoryRepository.syncFromFirestore()
        }
    }

    fun analyzeSpendingWithAi(startDateMillis: Long, endDateMillis: Long) {
        viewModelScope.launch {
            _errorMessage.value = null
            _isAiLoading.value = true
            try {
                val allExpenses = dao.getAllExpense().first()
                val (normalizedStart, normalizedEnd) = if (startDateMillis <= endDateMillis) {
                    Pair(startDateMillis, endDateMillis)
                } else {
                    Pair(endDateMillis, startDateMillis)
                }

                // Add 1 full day buffer to end date if needed so same-day selection includes full day
                val endBuffer = normalizedEnd + 86399999L

                val filteredHistory = allExpenses.filter { entity ->
                    val itemMillis = Utils.getMillisFromDate(entity.date)
                    itemMillis in normalizedStart..endBuffer
                }.sortedBy { Utils.getMillisFromDate(it.date) }

                if (filteredHistory.isEmpty()) {
                    _errorMessage.value = "선택한 기간에 해당하는 지출/수입 내역이 없습니다."
                    _isAiLoading.value = false
                    return@launch
                }

                val totalExpenseSum = filteredHistory
                    .filter { it.type.equals("Expense", ignoreCase = true) }
                    .sumOf { it.amount }
                val totalIncomeSum = filteredHistory
                    .filter { it.type.equals("Income", ignoreCase = true) }
                    .sumOf { it.amount }

                val startHuman = Utils.formatDateToHumanReadableForm(normalizedStart)
                val endHuman = Utils.formatDateToHumanReadableForm(normalizedEnd)
                val result = aiGateway.analyzeSpending(filteredHistory, startHuman, endHuman)

                result.onSuccess { resultData ->
                    _aiReport.value = resultData.report
                    val title = Utils.formatHistoryTitle(normalizedStart, normalizedEnd)
                    val promptTokens = resultData.promptTokens ?: 0
                    val candidatesTokens = resultData.candidatesTokens ?: 0
                    val totalTokens = resultData.totalTokens ?: (promptTokens + candidatesTokens)

                    val costUsd = Utils.calculateCostUsd(promptTokens, candidatesTokens)
                    val costKrw = Utils.calculateCostKrw(costUsd)

                    val entity = AiAnalysisEntity(
                        userId = currentUserId,
                        title = title,
                        startDate = startHuman,
                        endDate = endHuman,
                        transactionCount = filteredHistory.size,
                        totalExpenseSum = totalExpenseSum,
                        totalIncomeSum = totalIncomeSum,
                        geminiPrompt = resultData.geminiPrompt,
                        rawResponseJson = resultData.rawResponseJson,
                        responseTimeMs = resultData.responseTimeMs,
                        promptTokens = promptTokens,
                        candidatesTokens = candidatesTokens,
                        totalTokens = totalTokens,
                        estimatedCostUsd = costUsd,
                        estimatedCostKrw = costKrw,
                        modelName = resultData.modelName,
                        provider = resultData.provider,
                        authMethod = "Firebase App Check (Debug/Integrity)",
                        appCheckStatus = "Verified",
                        networkType = Utils.getNetworkType(context),
                        deviceModel = Utils.getDeviceModel(),
                        osVersion = Utils.getOsVersion(),
                        promptCharLength = resultData.geminiPrompt.length,
                        responseCharLength = resultData.rawResponseJson.length,
                        finishReason = "STOP",
                        summary = resultData.report.summary,
                        insights = resultData.report.insights.joinToString("\n"),
                        savingTips = resultData.report.savingTips.joinToString("\n"),
                        createdAt = System.currentTimeMillis()
                    )

                    val localId = aiHistoryRepository.saveReport(entity)
                    _lastSavedEntity.value = entity.copy(id = localId)
                }.onFailure {
                    _errorMessage.value = it.message ?: "알 수 없는 오류가 발생했습니다."
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "분석 중 오류가 발생했습니다."
            } finally {
                _isAiLoading.value = false
            }
        }
    }

    fun deleteHistoryReport(report: AiAnalysisEntity) {
        viewModelScope.launch {
            aiHistoryRepository.deleteReport(report)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun getEntriesForChart(entries: List<ExpenseSummary>): List<Entry> {
        val monthlyMap = mutableMapOf<Long, Double>()
        val calendar = java.util.Calendar.getInstance()

        for (entry in entries) {
            val millis = Utils.getMillisFromDate(entry.date)
            calendar.timeInMillis = millis
            // 해당 월의 1일로 설정하여 월별 그룹화
            calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)

            val monthStart = calendar.timeInMillis
            monthlyMap[monthStart] = (monthlyMap[monthStart] ?: 0.0) + entry.total_amount
        }

        val list = mutableListOf<Entry>()
        for ((monthStart, total) in monthlyMap) {
            list.add(Entry(monthStart.toFloat(), total.toFloat()))
        }
        return list.sortedBy { it.x }
    }

    override fun onEvent(event: UiEvent) {
    }
}
