package com.smartspend.ai.ai.analysis

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.smartspend.ai.ai.gateway.AiGateway
import com.smartspend.ai.ai.model.AiAnalysisReport
import com.smartspend.ai.base.BaseViewModel
import com.smartspend.ai.base.UiEvent
import com.smartspend.ai.data.dao.AiAnalysisDao
import com.smartspend.ai.data.dao.ExpenseDao
import com.smartspend.ai.data.model.AiAnalysisEntity
import com.smartspend.ai.data.model.ExpenseSummary
import com.smartspend.ai.data.repository.AiHistoryRepository
import com.smartspend.ai.utils.Utils
import com.github.mikephil.charting.data.Entry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import kotlin.collections.iterator

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val dao: ExpenseDao,
    private val aiAnalysisDao: AiAnalysisDao,
    private val aiHistoryRepository: AiHistoryRepository,
    private val aiGateway: AiGateway
) : BaseViewModel() {
    companion object {
        // ???∏ÏÖò ?ôÏïà ????Î≤àÎßå ?ÑÏãú Í∏∞Î°ù????†ú?òÎèÑÎ°??åÎûòÍ∑?Í¥ÄÎ¶?
        private var hasCleanedUpTemporaryReports = false
    }

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

    private val _shouldSaveToCloud = MutableStateFlow(true)
    val shouldSaveToCloud: StateFlow<Boolean> = _shouldSaveToCloud

    init {
        // ???§Ìñâ ??Ï≤?ViewModel ?ùÏÑ± ?úÏóêÎß??úÎ≤Ñ???Ä?•ÎêòÏßÄ ?äÏ?(?ÑÏãú) Î∂ÑÏÑù Í∏∞Î°ù ??†ú
        if (!hasCleanedUpTemporaryReports) {
            viewModelScope.launch {
                aiAnalysisDao.deleteTemporaryReports()
                hasCleanedUpTemporaryReports = true
                Log.d("AnalyticsViewModel", "Temporary reports cleaned up for this session.")
            }
        }

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

    fun analyzeSpendingWithAi(startDateMillis: Long, endDateMillis: Long, shouldSaveToCloud: Boolean = true) {
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

                // Í∞úÏÑ†: ?ïÌôï??ÎπÑÍµê Î∂ÑÏÑù???ÑÌï¥ ?ÑÏöî??Ï∂©Î∂Ñ??Í≥ºÍ±∞ ?∞Ïù¥?∞Î? ?ïÎ≥¥?©Îãà??
                // 1. ÏßÄ?úÎã¨ 1??(?ÑÏõî ?ôÍ∏∞ ?ÄÎπ?ÎπÑÍµê??
                // 2. ?ÑÏû¨Î°úÎ???60????(ÏµúÍ∑º 60???ÅÏÑ∏ ?¥Ïó≠??
                // ?????†Ïßú Ï§???Í≥ºÍ±∞???†ÏßúÎ•??úÏûë?êÏúºÎ°??°Ïäµ?àÎã§.
                val calendar = Calendar.getInstance()

                // ?§ÎäòÎ°úÎ???60????
                calendar.timeInMillis = System.currentTimeMillis()
                calendar.add(Calendar.DAY_OF_YEAR, -60)
                val sixtyDaysAgoMillis = calendar.timeInMillis

                // ÏßÄ?úÎã¨ 1??
                calendar.timeInMillis = normalizedStart
                calendar.add(Calendar.MONTH, -1)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val lastMonthFirstDayMillis = calendar.timeInMillis

                val expandedStartMillis = minOf(sixtyDaysAgoMillis, lastMonthFirstDayMillis)

                val filteredHistory = allExpenses.filter { entity ->
                    val itemMillis = Utils.getMillisFromDate(entity.date)
                    itemMillis in expandedStartMillis..endBuffer
                }.sortedBy { Utils.getMillisFromDate(it.date) }

                if (filteredHistory.isEmpty()) {
                    _errorMessage.value = "?†ÌÉù??Í∏∞Í∞Ñ???¥Îãπ?òÎäî ÏßÄÏ∂??òÏûÖ ?¥Ïó≠???ÜÏäµ?àÎã§."
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
                    val thoughtsTokens = resultData.thoughtsTokens ?: 0


                    val costUsd = Utils.calculateCostUsd(modelName = resultData.modelName, promptTokens = promptTokens, candidatesTokens = candidatesTokens, thoughtsTokens = thoughtsTokens)
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
                        agentVersion = resultData.agentVersion,
                        provider = resultData.provider,
                        authMethod = "Firebase App Check (Debug/Integrity)",
                        appCheckStatus = "Verified",
                        networkType = Utils.getNetworkType(context),
                        deviceModel = Utils.getDeviceModel(),
                        osVersion = Utils.getOsVersion(),
                        promptCharLength = resultData.geminiPrompt.length,
                        responseCharLength = resultData.rawResponseJson.length,
                        finishReason = "STOP",
                        summary = resultData.report.summary ?: "",
                        insights = resultData.report.insights?.joinToString("\n") ?: "",
                        savingTips = resultData.report.savingTips?.joinToString("\n") ?: "",
                        createdAt = System.currentTimeMillis()
                    )

                    Log.d("AnalyticsViewModel", "Calling saveReport with shouldSaveToCloud=$shouldSaveToCloud")
                    val localId = aiHistoryRepository.saveReport(entity, shouldSaveToCloud)
                    _lastSavedEntity.value = entity.copy(id = localId)
                }.onFailure {
                    _errorMessage.value = it.message ?: "?????ÜÎäî ?§Î•òÍ∞Ä Î∞úÏÉù?àÏäµ?àÎã§."
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Î∂ÑÏÑù Ï§??§Î•òÍ∞Ä Î∞úÏÉù?àÏäµ?àÎã§."
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

    fun setShouldSaveToCloud(value: Boolean) {
        _shouldSaveToCloud.value = value
    }

    fun getEntriesForChart(entries: List<ExpenseSummary>): List<Entry> {
        val monthlyMap = mutableMapOf<Long, Double>()
        val calendar = Calendar.getInstance()

        for (entry in entries) {
            val millis = Utils.getMillisFromDate(entry.date)
            calendar.timeInMillis = millis
            // ?¥Îãπ ?îÏùò 1?ºÎ°ú ?§Ï†ï?òÏó¨ ?îÎ≥Ñ Í∑∏Î£π??
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

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