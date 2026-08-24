package com.codewithfk.expensetracker.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.codewithfk.expensetracker.android.auth.LoginRoute
import com.codewithfk.expensetracker.android.feature.add_expense.AddExpense
import com.codewithfk.expensetracker.android.ai.chat_agent.AgentScreen
import com.codewithfk.expensetracker.android.feature.home.HomeScreen
import com.codewithfk.expensetracker.android.ai.analysis.AiHistoryScreen
import com.codewithfk.expensetracker.android.ai.analysis.AnalyticsScreen
import com.codewithfk.expensetracker.android.feature.transaction_detail.TransactionDetailScreen
import com.codewithfk.expensetracker.android.feature.transactionlist.TransactionListScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun NavHostScreen() {
    val navController = rememberNavController()
    val isUserLoggedIn = remember { Firebase.auth.currentUser != null }
    val startDestination = if (isUserLoggedIn) "/home" else "/login"

    var bottomBarVisibility by remember {
        mutableStateOf(isUserLoggedIn)
    }

    Scaffold(bottomBar = {
        AnimatedVisibility(visible = bottomBarVisibility) {
            NavigationBottomBar(
                navController = navController,
                items = listOf(
                    NavItem(route = "/home", icon = R.drawable.ic_home),
                    NavItem(route = "/analytics", icon = R.drawable.ic_stats),
                    NavItem(route = "/agent", icon = R.drawable.ic_gemini_color)
                )
            )
        }
    }) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(it)
        ) {
            composable(route = "/login") {
                bottomBarVisibility = false
                LoginRoute(navController)
            }

            composable(route = "/home") {
                bottomBarVisibility = true
                HomeScreen(navController)
            }

            composable(route = "/add_income") {
                bottomBarVisibility = false
                AddExpense(navController, isIncome = true)
            }
            composable(route = "/add_exp") {
                bottomBarVisibility = false
                AddExpense(navController, isIncome = false)
            }

            composable(route = "/analytics") {
                bottomBarVisibility = true
                AnalyticsScreen(navController)
            }
            composable(route = "/agent") {
                bottomBarVisibility = false
                AgentScreen()
            }
            composable(route = "/ai_history") {
                bottomBarVisibility = false
                AiHistoryScreen(navController)
            }
            composable(route = "/all_transactions") {
                bottomBarVisibility = true
                TransactionListScreen(navController)
            }
            composable(
                route = "/transaction_detail/{id}",
                arguments = listOf(navArgument("id") { type = NavType.IntType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getInt("id") ?: -1
                bottomBarVisibility = false
                TransactionDetailScreen(navController, id)
            }
        }
    }
}


data class NavItem(
    val route: String,
    val icon: Int
)

@Composable
fun NavigationBottomBar(
    navController: NavController,
    items: List<NavItem>
) {
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route

    // "Floating" 스타일의 둥근 하단 바
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp), // 바닥에서 띄우기 위한 패딩
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(30.dp), // 아주 둥근 모서리
            tonalElevation = 8.dp, // 떠 있는 느낌을 위한 그림자 (Elevation)
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val selected = currentRoute == item.route

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val isAgent = item.route == "/agent"
                        
                        Icon(
                            painter = painterResource(id = item.icon),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = if (isAgent) {
                                Color.Unspecified 
                            } else {
                                if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                            }
                        )
                    }
                }
            }
        }
    }
}
