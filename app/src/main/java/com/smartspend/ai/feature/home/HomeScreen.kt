package com.smartspend.ai.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.smartspend.ai.data.model.ExpenseEntity
import com.smartspend.ai.ui.theme.Zinc
import com.smartspend.ai.widget.ExpenseTextView
import com.smartspend.ai.R
import com.smartspend.ai.base.HomeNavigationEvent
import com.smartspend.ai.base.NavigationEvent
import com.smartspend.ai.ui.theme.Green
import com.smartspend.ai.ui.theme.LightGrey
import com.smartspend.ai.ui.theme.Red
import com.smartspend.ai.ui.theme.Typography
import com.smartspend.ai.utils.Utils
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth


@Composable
fun HomeScreen(navController: NavController, viewModel: HomeViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                NavigationEvent.NavigateBack -> navController.popBackStack()
                HomeNavigationEvent.NavigateToSeeAll -> {
                    navController.navigate("/all_transactions")
                }

                HomeNavigationEvent.NavigateToAddIncome -> {
                    navController.navigate("/add_income")
                }

                HomeNavigationEvent.NavigateToAddExpense -> {
                    navController.navigate("/add_exp")
                }

                else -> {}
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        ConstraintLayout(modifier = Modifier.fillMaxSize()) {
            val (nameRow, list, card, topBar, add) = createRefs()
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .constrainAs(topBar) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                })
            val currentUser = remember { FirebaseAuth.getInstance().currentUser }
            val userName = remember(currentUser) {
                currentUser?.displayName?.takeIf { it.isNotBlank() }
                    ?: currentUser?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                    ?: "?¨Ïö©??
            }

            Box(modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .constrainAs(nameRow) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }) {
                Column(modifier = Modifier.align(Alignment.CenterStart)) {
                    ExpenseTextView(
                        text = "?àÎÖï?òÏÑ∏??,
                        style = Typography.bodyMedium,
                    )
                    ExpenseTextView(
                        text = "${userName}??,
                        style = Typography.titleLarge,
                    )
                }
                Icon(
                    painter = painterResource(id = R.drawable.ic_notification),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            val state = viewModel.expenses.collectAsState(initial = emptyList())
            val lastMonthIncome = viewModel.getLastMonthIncome(state.value)
            val thisMonthIncome = viewModel.getThisMonthIncome(state.value)
            val thisMonthExpense = viewModel.getThisMonthExpense(state.value)
            val totalBalance = viewModel.getTotalBalance(state.value)
            val netProfit = viewModel.getNetProfit(state.value)
            CardItem(
                modifier = Modifier.constrainAs(card) {
                    top.linkTo(nameRow.bottom)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                },
                totalBalance = totalBalance,
                netProfit = netProfit,
                lastMonthIncome = lastMonthIncome,
                income = thisMonthIncome,
                expense = thisMonthExpense
            )
            TransactionList(
                modifier = Modifier
                    .fillMaxWidth()
                    .constrainAs(list) {
                        top.linkTo(card.bottom)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        bottom.linkTo(parent.bottom)
                        height = Dimension.fillToConstraints
                    },
                list = state.value.reversed(),
                onSeeAllClicked = {
                    viewModel.onEvent(HomeUiEvent.OnSeeAllClicked)
                },
                onTransactionClicked = {
                    navController.navigate("/transaction_detail/${it.id}")
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .constrainAs(add) {
                        bottom.linkTo(parent.bottom)
                        end.linkTo(parent.end)
                    }, contentAlignment = Alignment.BottomEnd
            ) {
                MultiFloatingActionButton(modifier = Modifier, {
                    viewModel.onEvent(HomeUiEvent.OnAddExpenseClicked)
                }, {
                    viewModel.onEvent(HomeUiEvent.OnAddIncomeClicked)
                }, {
                    viewModel.onEvent(HomeUiEvent.OnSeedDataClicked)
                })
            }
        }
    }
}

@Composable
fun MultiFloatingActionButton(
    modifier: Modifier,
    onAddExpenseClicked: () -> Unit,
    onAddIncomeClicked: () -> Unit,
    onSeedDataClicked: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Secondary FABs
            AnimatedVisibility(visible = expanded) {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(color = Zinc, shape = RoundedCornerShape(12.dp))
                            .clickable {
                                onSeedDataClicked.invoke()
                                expanded = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_notification), // Using notification icon as a placeholder for seed
                            contentDescription = "?∞Ïù¥???ùÏÑ±",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(color = Zinc, shape = RoundedCornerShape(12.dp))
                            .clickable {
                                onAddIncomeClicked.invoke()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_income),
                            contentDescription = "?òÏûÖ Ï∂îÍ?",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(color = Zinc, shape = RoundedCornerShape(12.dp))
                            .clickable {
                                onAddExpenseClicked.invoke()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_expense),
                            contentDescription = "ÏßÄÏ∂?Ï∂îÍ?",
                            tint = Color.White
                        )
                    }
                }
            }
            // Main FAB
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(color = Zinc)
                    .clickable {
                        expanded = !expanded
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_addbutton),
                    contentDescription = "small floating action button",
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

@Composable
fun CardItem(
    modifier: Modifier,
    totalBalance: String,
    netProfit: String,
    lastMonthIncome: String,
    income: String,
    expense: String
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Zinc)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                ExpenseTextView(
                    text = "Ï¥??îÏï°",
                    style = Typography.titleSmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.size(4.dp))
                ExpenseTextView(
                    text = totalBalance,
                    style = Typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White.copy(alpha = 0.2f)
            ) {
                ExpenseTextView(
                    text = "?ÑÏ†Å $netProfit",
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CardRowItem(
                modifier = Modifier,
                title = "?¥Î≤à???òÏûÖ",
                amount = income,
                imaget = R.drawable.ic_income
            )
            CardRowItem(
                modifier = Modifier,
                title = "?¥Î≤à??ÏßÄÏ∂?,
                amount = expense,
                imaget = R.drawable.ic_expense
            )
            CardRowItem(
                modifier = Modifier,
                title = "ÏßÄ?úÎã¨ ?òÏûÖ",
                amount = lastMonthIncome,
                imaget = R.drawable.ic_income
            )
        }
    }
}


@Composable
fun TransactionList(
    modifier: Modifier,
    list: List<ExpenseEntity>,
    title: String = "ÏµúÍ∑º Í±∞Îûò ?¥Ïó≠",
    onSeeAllClicked: () -> Unit,
    onTransactionClicked: (ExpenseEntity) -> Unit
) {
    val filteredList = remember(list) {
        val calendar = java.util.Calendar.getInstance()
        // ?ÑÏû¨ ?úÍ∞Ñ????Î∂?Ï¥?Ï¥àÍ∏∞??
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)

        // ?§Îäò(8??23?? Í∏∞Ï?, ?ëÎÖÑ 9??1?ºÎ????úÏûë?òÍ∏∞ ?ÑÌï¥ 11Í∞úÏõî ?ÑÏùò 1?ºÎ°ú ?§Ï†ï
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
        calendar.add(java.util.Calendar.MONTH, -11)
        val startTime = calendar.timeInMillis

        list.filter {
            val itemTime = Utils.getMillisFromDate(it.date)
            itemTime >= startTime
        }
    }

    LazyColumn(modifier = modifier.padding(horizontal = 16.dp)) {
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    ExpenseTextView(
                        text = title,
                        style = Typography.titleLarge,
                    )
                    if (title == "ÏµúÍ∑º Í±∞Îûò ?¥Ïó≠") {
                        ExpenseTextView(
                            text = "?ÑÏ≤¥Î≥¥Í∏∞",
                            style = Typography.bodyMedium,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .clickable {
                                    onSeeAllClicked.invoke()
                                }
                        )
                    }
                }
                Spacer(modifier = Modifier.size(12.dp))
            }
        }
        items(items = list,//filteredList,
            key = { item -> item.id ?: 0 }) { item ->
            val icon = Utils.getItemIcon(item)
            val amount = if (item.type == "Income") item.amount else item.amount * -1

            TransactionItem(
                title = item.title,
                category = item.category,
                amount = Utils.formatCurrency(amount),
                icon = icon,
                date = Utils.formatStringDateToMonthDayYear(item.date),
                color = if (item.type == "Income") Green else Red,
                modifier = Modifier.clickable { onTransactionClicked(item) }
            )
        }
    }
}

@Composable
fun TransactionItem(
    title: String,
    category: String = "",
    amount: String,
    icon: Int,
    date: String,
    color: Color,
    modifier: Modifier
) {

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(end = 100.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.size(10.dp))
            Column {
                ExpenseTextView(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.size(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (category.isNotBlank()) {
                        ExpenseTextView(
                            text = category,
                            fontSize = 12.sp,
                            color = Zinc,
                            fontWeight = FontWeight.SemiBold
                        )
                        ExpenseTextView(
                            text = " ??",
                            fontSize = 12.sp,
                            color = LightGrey
                        )
                    }
                    ExpenseTextView(text = date, fontSize = 12.sp, color = LightGrey)
                }
            }
        }
        ExpenseTextView(
            text = amount,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterEnd),
            color = color
        )
    }
}

@Composable
fun CardRowItem(modifier: Modifier, title: String, amount: String, imaget: Int) {
    Column(modifier = modifier) {
        Row {

            Image(
                painter = painterResource(id = imaget),
                contentDescription = null,
            )
            Spacer(modifier = Modifier.size(8.dp))
            ExpenseTextView(text = title, style = Typography.bodyLarge, color = Color.White)
        }
        Spacer(modifier = Modifier.size(4.dp))
        ExpenseTextView(text = amount, style = Typography.titleLarge, color = Color.White)
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    HomeScreen(rememberNavController())
}