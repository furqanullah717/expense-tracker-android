package com.codewithfk.expensetracker.android.feature.stats

import android.view.LayoutInflater
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.codewithfk.expensetracker.android.R
import com.codewithfk.expensetracker.android.utils.Utils
import com.codewithfk.expensetracker.android.feature.home.TransactionList
import com.codewithfk.expensetracker.android.viewmodel.StatsViewModel
import com.codewithfk.expensetracker.android.widget.ExpenseTextView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineDataSet
import androidx.core.graphics.toColorInt

@Composable
fun StatsScreen(navController: NavController, viewModel: StatsViewModel= hiltViewModel()) {
    Scaffold(topBar = {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_back),
                contentDescription = null,
                modifier = Modifier.align(
                    Alignment.CenterStart
                ),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
            )
            ExpenseTextView(
                text = "Statistics",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.Center)
            )
            Image(
                painter = painterResource(id = R.drawable.dots_menu),
                contentDescription = null,
                modifier = Modifier.align(Alignment.CenterEnd),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
            )
        }
    }) {
        val dataState = viewModel.entries.collectAsState(emptyList())
        val topExpense = viewModel.topEntries.collectAsState(initial = emptyList())
        Column(modifier = Modifier.padding(it)) {
            val entries = viewModel.getEntriesForChart(dataState.value)
            LineChart(entries = entries)
            Spacer(modifier = Modifier.height(16.dp))
            TransactionList(Modifier, list = topExpense.value, "Top Spending")
        }
    }
}

@Composable
fun LineChart(entries: List<Entry>) {
    val context = LocalContext.current
    val isDarkMode = MaterialTheme.colorScheme.background.luminance() < 0.5
    var textColor = if (isDarkMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
    val primaryColor = "#FF2F7E79".toColorInt()

    AndroidView(
        factory = {
            val view = LayoutInflater.from(context).inflate(R.layout.stats_line_chart, null)
            view
        }, modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
    ) { view ->
        val lineChart = view.findViewById<LineChart>(R.id.lineChart)

        val dataSet = LineDataSet(entries, "Expenses").apply {
            color = primaryColor
            valueTextColor = textColor
            lineWidth = 3f
            axisDependency = YAxis.AxisDependency.RIGHT
            setDrawFilled(true)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            valueTextSize = 12f
            valueTextColor = primaryColor
            val drawable = ContextCompat.getDrawable(context, R.drawable.char_gradient)
            drawable?.let {
                fillDrawable = it
            }
        }

        lineChart.apply {
            xAxis.apply {
                valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return Utils.formatDateForChart(value.toLong())
                    }
                }
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(false)
            }

            axisLeft.apply {
                isEnabled = false
                setDrawGridLines(false)
            }

            axisRight.apply {
                isEnabled = false
                setDrawGridLines(false)
                textColor = textColor
            }

            legend.textColor = textColor
            description.textColor = textColor
            
            setBackgroundColor(if (isDarkMode) android.graphics.Color.TRANSPARENT else android.graphics.Color.WHITE)
            data = com.github.mikephil.charting.data.LineData(dataSet)
            invalidate()
        }
    }
}