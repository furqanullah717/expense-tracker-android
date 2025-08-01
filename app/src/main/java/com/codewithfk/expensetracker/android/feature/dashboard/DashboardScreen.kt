package com.codewithfk.expensetracker.android.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.codewithfk.expensetracker.android.data.model.ExpenseSummary
import com.codewithfk.expensetracker.android.ui.theme.Typography
import com.codewithfk.expensetracker.android.ui.theme.Zinc

@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val expenseSummary by viewModel.expenseSummary.collectAsState(initial = emptyList())
    val monthlyStats by viewModel.monthlyStats.collectAsState(initial = null)
    val categoryBreakdown by viewModel.categoryBreakdown.collectAsState(initial = emptyList())

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Monthly Overview Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Zinc)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Monthly Overview",
                        style = Typography.titleLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    monthlyStats?.let { stats ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatItem("Total Income", stats.totalIncome)
                            StatItem("Total Expenses", stats.totalExpenses)
                            StatItem("Savings", stats.savings)
                        }
                    }
                }
            }

            // Category Breakdown
            Text(
                text = "Expense Categories",
                style = Typography.titleMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            categoryBreakdown.forEach { category ->
                CategoryProgressBar(
                    category = category.category,
                    amount = category.amount,
                    percentage = category.percentage
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Recent Transactions Summary
            Text(
                text = "Recent Activity",
                style = Typography.titleMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            expenseSummary.take(5).forEach { summary ->
                TransactionSummaryItem(summary)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = Typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f)
        )
        Text(
            text = "₹%.2f".format(value),
            style = Typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CategoryProgressBar(
    category: String,
    amount: Double,
    percentage: Float
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = category, style = Typography.bodyMedium)
            Text(text = "₹%.2f".format(amount), style = Typography.bodyMedium)
        }
        LinearProgressIndicator(
            progress = percentage,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .padding(top = 4.dp),
            color = Zinc
        )
    }
}

@Composable
fun TransactionSummaryItem(summary: ExpenseSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = summary.type, style = Typography.bodyLarge)
                Text(
                    text = summary.date,
                    style = Typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "₹%.2f".format(summary.total_amount),
                style = Typography.titleMedium,
                color = if (summary.type == "Income") Color.Green else Color.Red
            )
        }
    }
}