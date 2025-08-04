package com.codewithfk.expensetracker.android.feature.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.codewithfk.expensetracker.android.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.codewithfk.expensetracker.android.data.model.ExpenseSummary
import com.codewithfk.expensetracker.android.ui.theme.LightPrimary
import com.codewithfk.expensetracker.android.ui.theme.Typography
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val expenseSummary by viewModel.expenseSummary.collectAsState(initial = emptyList())
    val monthlyStats by viewModel.monthlyStats.collectAsState(initial = null)
    val categoryBreakdown by viewModel.categoryBreakdown.collectAsState(initial = emptyList())
    val currentDate = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date()) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header Section with Greeting and Date
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LightPrimary)
                    .padding(24.dp)
            ) {
                Column {
                    Text(
                        text = "Dashboard",
                        style = Typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentDate,
                        style = Typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            // Quick Actions
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(quickActions) { action ->
                    QuickActionButton(
                        icon = action.icon,
                        label = action.label,
                        onClick = {
                            when (action.label) {
                                "Add Expense" -> navController.navigate("/add_exp")
                                "Add Income" -> navController.navigate("/add_income")
                                "Analytics" -> navController.navigate("/stats") {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                                "Overview" -> navController.navigate("/home") {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }

            // Monthly Overview Cards
            monthlyStats?.let { stats ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "Income",
                        amount = stats.totalIncome,
                        icon = R.drawable.ic_income,
                        color = Color(0xFF4CAF50)
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "Expenses",
                        amount = stats.totalExpenses,
                        icon = R.drawable.ic_expense,
                        color = Color(0xFFE91E63)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                StatCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    title = "Net Savings",
                    amount = stats.savings,
                    icon = R.drawable.ic_stats,
                    color = Color(0xFF2196F3)
                )
            }

            // Category Breakdown
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Expense Breakdown",
                        style = Typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    categoryBreakdown.forEach { category ->
                        CategoryProgressBarAnimated(
                            category = category.category,
                            amount = category.amount,
                            percentage = category.percentage
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            // Recent Transactions
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Transactions",
                            style = Typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { /* Handle view all click */ }) {
                            Text("View All")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    expenseSummary.take(5).forEach { summary ->
                        TransactionItem(summary)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

data class QuickAction(
    val icon: Int,
    val label: String
)

val quickActions = listOf(
    QuickAction(R.drawable.ic_expense, "Add Expense"),
    QuickAction(R.drawable.ic_income, "Add Income"),
    QuickAction(R.drawable.ic_stats, "Analytics"),
    QuickAction(R.drawable.ic_home, "Overview")
)

@Composable
fun QuickActionButton(
    icon: Int,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = icon),
                contentDescription = label,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = Typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    amount: Double,
    icon: Int,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = Typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = icon),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "₹%.2f".format(amount),
                style = Typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun CategoryProgressBarAnimated(
    category: String,
    amount: Double,
    percentage: Float
) {
    val animatedPercentage by animateFloatAsState(
        targetValue = percentage,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "Progress Animation"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category,
                style = Typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "₹%.2f".format(amount),
                style = Typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { animatedPercentage },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = LightPrimary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun TransactionItem(summary: ExpenseSummary) {
    val isIncome = summary.type == "Income"
    val transactionColor = if (isIncome) Color(0xFF4CAF50) else Color(0xFFE91E63)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(transactionColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = if (isIncome) R.drawable.ic_income else R.drawable.ic_expense),
                    contentDescription = null,
                    tint = transactionColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = summary.type,
                    style = Typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = summary.date,
                    style = Typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = "₹%.2f".format(summary.total_amount),
            style = Typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = transactionColor
        )
    }
}