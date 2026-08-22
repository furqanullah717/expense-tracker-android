@file:OptIn(ExperimentalMaterial3Api::class)

package com.codewithfk.expensetracker.android.feature.stats

import android.view.LayoutInflater
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.codewithfk.expensetracker.android.R
import com.codewithfk.expensetracker.android.data.ai.model.AiAnalysisReport
import com.codewithfk.expensetracker.android.data.model.AiAnalysisEntity
import com.codewithfk.expensetracker.android.feature.home.TransactionList
import com.codewithfk.expensetracker.android.ui.theme.Zinc
import com.codewithfk.expensetracker.android.utils.Utils
import com.codewithfk.expensetracker.android.widget.ExpenseTextView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineDataSet
import java.util.Calendar

@Composable
fun StatsScreen(navController: NavController, viewModel: StatsViewModel = hiltViewModel()) {
    var showDateRangeDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_back),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clickable {
                        navController.navigateUp()
                    },
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.outline)
            )
            ExpenseTextView(
                text = "통계",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.Center)
            )
            Image(
                painter = painterResource(id = R.drawable.dots_menu),
                contentDescription = "AI 분석 기록",
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable {
                        navController.navigate("/ai_history")
                    }
                    .padding(4.dp),
                colorFilter = ColorFilter.tint(Color.Black)
            )
        }
    }) { paddingValues ->
        val dataState = viewModel.entries.collectAsState(emptyList())
        val topExpense = viewModel.topEntries.collectAsState(initial = emptyList())
        val aiReport = viewModel.aiReport.collectAsState()
        val lastSavedEntity = viewModel.lastSavedEntity.collectAsState()
        val isAiLoading = viewModel.isAiLoading.collectAsState()
        val errorMessage = viewModel.errorMessage.collectAsState()

        if (errorMessage.value != null) {
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("확인")
                    }
                },
                title = { Text("알림") },
                text = { Text(errorMessage.value ?: "") }
            )
        }

        if (showDateRangeDialog) {
            DateRangeSelectionDialog(
                onDismiss = { showDateRangeDialog = false },
                onConfirm = { startMillis, endMillis ->
                    showDateRangeDialog = false
                    viewModel.analyzeSpendingWithAi(startMillis, endMillis)
                }
            )
        }

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            val entries = viewModel.getEntriesForChart(dataState.value)
            LineChart(entries = entries)

            Spacer(modifier = Modifier.height(16.dp))

            // AI Analysis Section
            AiAnalysisSection(
                report = aiReport.value,
                lastSaved = lastSavedEntity.value,
                isLoading = isAiLoading.value,
                onAnalyzeClick = { showDateRangeDialog = true },
                onViewHistoryClick = { navController.navigate("/ai_history") }
            )

            Spacer(modifier = Modifier.height(16.dp))
            TransactionList(
                Modifier.height(400.dp),
                list = topExpense.value,
                "주요 지출 항목",
                onSeeAllClicked = {},
                onTransactionClicked = {
                    navController.navigate("/transaction_detail/${it.id}")
                }
            )
        }
    }
}

@Composable
fun DateRangeSelectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (startMillis: Long, endMillis: Long) -> Unit
) {
    val now = remember { System.currentTimeMillis() }
    val calendar = remember { Calendar.getInstance() }

    // Default start: 1 month ago or start of current year
    val defaultStart = remember {
        calendar.timeInMillis = now
        calendar.add(Calendar.MONTH, -1)
        calendar.timeInMillis
    }

    var startDateMillis by remember { mutableLongStateOf(defaultStart) }
    var endDateMillis by remember { mutableLongStateOf(now) }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    if (showStartPicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDateMillis)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        startDateMillis = it
                    }
                    showStartPicker = false
                }) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) {
                    Text("취소")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndPicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = endDateMillis)
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        endDateMillis = it
                    }
                    showEndPicker = false
                }) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) {
                    Text("취소")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            ExpenseTextView(
                text = "분석 기간 선택",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Zinc
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ExpenseTextView(
                    text = "AI 소비 분석을 진행할 시작일과 종료일을 선택하세요.",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Start Date Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF2F4F7))
                        .clickable { showStartPicker = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpenseTextView(text = "시작일", fontSize = 13.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.weight(1f))
                    ExpenseTextView(
                        text = Utils.formatDateToKorean(startDateMillis),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // End Date Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF2F4F7))
                        .clickable { showEndPicker = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpenseTextView(text = "종료일", fontSize = 13.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.weight(1f))
                    ExpenseTextView(
                        text = Utils.formatDateToKorean(endDateMillis),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Preset Buttons
                ExpenseTextView(
                    text = "빠른 선택",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(6.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QuickPresetChip(title = "최근 1개월") {
                        val cal = Calendar.getInstance()
                        endDateMillis = now
                        cal.timeInMillis = now
                        cal.add(Calendar.MONTH, -1)
                        startDateMillis = cal.timeInMillis
                    }
                    QuickPresetChip(title = "최근 3개월") {
                        val cal = Calendar.getInstance()
                        endDateMillis = now
                        cal.timeInMillis = now
                        cal.add(Calendar.MONTH, -3)
                        startDateMillis = cal.timeInMillis
                    }
                    QuickPresetChip(title = "올해 전체") {
                        val cal = Calendar.getInstance()
                        endDateMillis = now
                        cal.timeInMillis = now
                        cal.set(Calendar.MONTH, Calendar.JANUARY)
                        cal.set(Calendar.DAY_OF_MONTH, 1)
                        startDateMillis = cal.timeInMillis
                    }
                    QuickPresetChip(title = "최근 1년") {
                        val cal = Calendar.getInstance()
                        endDateMillis = now
                        cal.timeInMillis = now
                        cal.add(Calendar.YEAR, -1)
                        startDateMillis = cal.timeInMillis
                    }
                    QuickPresetChip(title = "최근 2년") {
                        val cal = Calendar.getInstance()
                        endDateMillis = now
                        cal.timeInMillis = now
                        cal.add(Calendar.YEAR, -2)
                        startDateMillis = cal.timeInMillis
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(startDateMillis, endDateMillis) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Zinc)
            ) {
                ExpenseTextView(text = "분석 시작", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                ExpenseTextView(text = "취소")
            }
        }
    )
}

@Composable
fun QuickPresetChip(title: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFE8ECEF)
    ) {
        ExpenseTextView(
            text = title,
            fontSize = 11.sp,
            color = Color(0xFF263238),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun AiAnalysisSection(
    report: AiAnalysisReport?,
    lastSaved: AiAnalysisEntity?,
    isLoading: Boolean,
    onAnalyzeClick: () -> Unit,
    onViewHistoryClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Zinc.copy(alpha = 0.1f))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                ExpenseTextView(
                    text = "AI 소비 분석",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Zinc
                )
                ExpenseTextView(
                    text = "기록 보기 >",
                    fontSize = 12.sp,
                    color = Zinc,
                    modifier = Modifier.clickable { onViewHistoryClick() }
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Zinc)
            } else {
                Button(
                    onClick = onAnalyzeClick,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    ExpenseTextView(text = "분석하기", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        report?.let {
            Spacer(modifier = Modifier.height(12.dp))

            if (lastSaved != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Zinc.copy(alpha = 0.15f)
                    ) {
                        ExpenseTextView(
                            text = "⏱️ 소요시간: ${Utils.formatDurationMs(lastSaved.responseTimeMs)}",
                            fontSize = 11.sp,
                            color = Zinc,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Zinc.copy(alpha = 0.15f)
                    ) {
                        ExpenseTextView(
                            text = "🔢 ${lastSaved.transactionCount}건 분석됨",
                            fontSize = 11.sp,
                            color = Zinc,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            ExpenseTextView(text = "📊 요약", fontWeight = FontWeight.Bold)
            ExpenseTextView(text = it.summary, fontSize = 14.sp)

            Spacer(modifier = Modifier.height(12.dp))
            ExpenseTextView(text = "💡 주요 인사이트", fontWeight = FontWeight.Bold)
            it.insights.forEach { insight ->
                ExpenseTextView(text = "• $insight", fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))
            ExpenseTextView(text = "💰 절약 팁", fontWeight = FontWeight.Bold)
            it.savingTips.forEach { tip ->
                ExpenseTextView(text = "• $tip", fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun LineChart(entries: List<Entry>) {
    val context = LocalContext.current
    AndroidView(
        factory = {
            val view = LayoutInflater.from(context).inflate(R.layout.stats_line_chart, null)
            view
        }, modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
    ) { view ->
        val lineChart = view.findViewById<LineChart>(R.id.lineChart)

        val dataSet = LineDataSet(entries, "지출 내역").apply {
            color = android.graphics.Color.parseColor("#FF2F7E79")
            valueTextColor = android.graphics.Color.BLACK
            lineWidth = 3f
            axisDependency = YAxis.AxisDependency.RIGHT
            setDrawFilled(true)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            valueTextSize = 12f
            valueTextColor = android.graphics.Color.parseColor("#FF2F7E79")
            val drawable = ContextCompat.getDrawable(context, R.drawable.char_gradient)
            drawable?.let {
                fillDrawable = it
            }

        }

        lineChart.xAxis.valueFormatter =
            object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return Utils.formatDateForChart(value.toLong())
                }
            }
        lineChart.data = com.github.mikephil.charting.data.LineData(dataSet)
        lineChart.description.isEnabled = false
        lineChart.legend.isEnabled = false
        lineChart.xAxis.granularity = 86400000f // 1 day in ms
        lineChart.xAxis.labelCount = 5
        lineChart.axisLeft.isEnabled = false
        lineChart.axisRight.isEnabled = false
        lineChart.axisRight.setDrawGridLines(false)
        lineChart.axisLeft.setDrawGridLines(false)
        lineChart.xAxis.setDrawGridLines(false)
        lineChart.xAxis.setDrawAxisLine(false)
        lineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        lineChart.invalidate()
    }
}
