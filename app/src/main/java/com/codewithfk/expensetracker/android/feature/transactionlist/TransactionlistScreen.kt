package com.codewithfk.expensetracker.android.feature.transactionlist

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.codewithfk.expensetracker.android.R
import com.codewithfk.expensetracker.android.feature.home.HomeViewModel
import com.codewithfk.expensetracker.android.feature.home.TransactionItem
import com.codewithfk.expensetracker.android.utils.Utils
import com.codewithfk.expensetracker.android.widget.ExpenseTextView
import java.util.Calendar

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state = viewModel.expenses.collectAsState(initial = emptyList())
    var filterType by remember { mutableStateOf("전체") }
    var dateRange by remember { mutableStateOf("전체 기간") }
    var sortAscending by remember { mutableStateOf(false) } // false: 최신순, true: 오래된순
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val filteredTransactions = remember(filterType, state.value) {
        when (filterType) {
            "지출" -> state.value.filter { it.type == "Expense" }
            "수입" -> state.value.filter { it.type == "Income" }
            else -> state.value
        }
    }

    val filteredByDateRange = remember(filteredTransactions, dateRange) {
        filteredTransactions.filter { transaction ->
            val itemMillis = Utils.getMillisFromDate(transaction.date)
            val calNow = Calendar.getInstance()
            val calItem = Calendar.getInstance().apply { timeInMillis = itemMillis }

            when (dateRange) {
                "오늘" -> {
                    calNow.get(Calendar.YEAR) == calItem.get(Calendar.YEAR) &&
                            calNow.get(Calendar.DAY_OF_YEAR) == calItem.get(Calendar.DAY_OF_YEAR)
                }
                "어제" -> {
                    val calYesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                    calYesterday.get(Calendar.YEAR) == calItem.get(Calendar.YEAR) &&
                            calYesterday.get(Calendar.DAY_OF_YEAR) == calItem.get(Calendar.DAY_OF_YEAR)
                }
                "최근 30일" -> {
                    val cal30DaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }
                    itemMillis >= cal30DaysAgo.timeInMillis
                }
                "최근 90일" -> {
                    val cal90DaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -90) }
                    itemMillis >= cal90DaysAgo.timeInMillis
                }
                "최근 1년" -> {
                    val cal1YearAgo = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }
                    itemMillis >= cal1YearAgo.timeInMillis
                }
                else -> true
            }
        }
    }

    val finalTransactions = remember(filteredByDateRange, sortAscending) {
        if (sortAscending) {
            filteredByDateRange.sortedBy { Utils.getMillisFromDate(it.date) }
        } else {
            filteredByDateRange.sortedByDescending { Utils.getMillisFromDate(it.date) }
        }
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_back),
                    contentDescription = "Back",
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable { navController.popBackStack() }
                        .size(24.dp),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )

                ExpenseTextView(
                    text = "거래 내역",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.Center)
                )

                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 정렬 토글 버튼
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { sortAscending = !sortAscending }
                            .size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (sortAscending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Sort",
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 심플한 필터 버튼
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface)
                            .clickable { showBottomSheet = true }
                            .size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_filter),
                            contentDescription = "Filter",
                            modifier = Modifier.size(20.dp),
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.surface)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                items(finalTransactions) { item ->
                    val icon = Utils.getItemIcon(item)
                    TransactionItem(
                        title = item.title,
                        category = item.category,
                        amount = Utils.formatCurrency(item.amount),
                        icon = icon,
                        date = item.date,
                        color = if (item.type == "Income") Color(0xFF4CAF50) else Color(0xFFFF6B6B),
                        modifier = Modifier
                            .animateItem(tween(100))
                            .clickable {
                                navController.navigate("/transaction_detail/${item.id}")
                            }
                    )
                }
            }

            if (showBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showBottomSheet = false },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    dragHandle = null,
                    properties = ModalBottomSheetProperties(
                        shouldDismissOnBackPress = true,
                        isFocusable = true,
                        securePolicy = SecureFlagPolicy.Inherit
                    )
                ) {
                    FilterBottomSheetContent(
                        currentFilterType = filterType,
                        currentDateRange = dateRange,
                        onFilterChanged = { newFilterType, newDateRange ->
                            filterType = newFilterType
                            dateRange = newDateRange
                        },
                        onDismiss = { showBottomSheet = false }
                    )
                }
            }
        }
    }
}

@Composable
fun FilterBottomSheetContent(
    currentFilterType: String,
    currentDateRange: String,
    onFilterChanged: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        // 상단 핸들 바
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }

        // 헤더: 제목 + 닫기 버튼
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpenseTextView(
                text = "필터",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 닫기 버튼
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onDismiss() }
                    .size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    modifier = Modifier.size(18.dp),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 거래 유형 섹션
        ExpenseTextView(
            text = "거래 유형",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val typeOptions = listOf(
                "전체" to R.drawable.ic_home,
                "지출" to R.drawable.ic_expense,
                "수입" to R.drawable.ic_income
            )

            typeOptions.forEach { (type, _) ->
                val isSelected = currentFilterType == type
                ModernFilterChip(
                    selected = isSelected,
                    onClick = { onFilterChanged(type, currentDateRange) },
                    label = type,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 기간 섹션
        ExpenseTextView(
            text = "기간",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Column(
            modifier = Modifier.padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val dateOptions = listOf(
                "전체 기간" to "📅",
                "오늘" to "☀️",
                "어제" to "🌙",
                "최근 30일" to "📊",
                "최근 90일" to "📈",
                "최근 1년" to "📆"
            )

            dateOptions.forEach { (range, emoji) ->
                val isSelected = currentDateRange == range
                ModernDateChip(
                    selected = isSelected,
                    onClick = { onFilterChanged(currentFilterType, range) },
                    label = range,
                    emoji = emoji
                )
            }
        }

        // 적용 버튼
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.onSurface)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            ExpenseTextView(
                text = "적용 완료",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@Composable
fun ModernFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpenseTextView(
                text = when(label) {
                    "전체" -> "📋"
                    "지출" -> "💸"
                    "수입" -> "💰"
                    else -> "📌"
                },
                fontSize = 16.sp,
                modifier = Modifier.padding(end = 4.dp)
            )

            ExpenseTextView(
                text = label,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ModernDateChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    emoji: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else Color.Transparent
            )
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExpenseTextView(
            text = emoji,
            fontSize = 18.sp,
            modifier = Modifier.width(32.dp)
        )

        ExpenseTextView(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )

        if (selected) {
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}