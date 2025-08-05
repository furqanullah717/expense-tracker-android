package com.codewithfk.expensetracker.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.launch
import com.codewithfk.expensetracker.android.ui.components.DrawerContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.codewithfk.expensetracker.android.feature.add_expense.AddExpense
import com.codewithfk.expensetracker.android.feature.dashboard.DashboardScreen
import com.codewithfk.expensetracker.android.feature.home.HomeScreen
import com.codewithfk.expensetracker.android.feature.stats.StatsScreen
import com.codewithfk.expensetracker.android.feature.settings.SettingsScreen
import com.codewithfk.expensetracker.android.ui.theme.LightPrimary
import androidx.compose.runtime.rememberCoroutineScope
import com.codewithfk.expensetracker.android.ui.theme.ThemeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavHostScreen(themeViewModel: ThemeViewModel)
{
    val navController = rememberNavController()
    var bottomBarVisibility by remember { mutableStateOf(true) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "/home"
    val showMenuButton = currentRoute == "/home"

    ModalNavigationDrawer(
        drawerState = drawerState, drawerContent = {
            ModalDrawerSheet {
                DrawerContent(
                    navController = navController, themeViewModel = themeViewModel, onCloseDrawer = {
                        scope.launch { drawerState.close() }
                    })
            }
        }) {
        Scaffold(topBar = {
            if (showMenuButton)
            {
                CenterAlignedTopAppBar(title = { }, actions = {
                    IconButton(
                        onClick = {
                            scope.launch { drawerState.open() }
                        }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_menu),
                            contentDescription = "Menu"
                        )
                    }
                })
            }
        }, bottomBar = {
            AnimatedVisibility(visible = bottomBarVisibility) {
                NavigationBottomBar(
                    navController = navController, items = listOf(
                        NavItem(route = "/home", icon = R.drawable.ic_home),
                        NavItem(route = "/dashboard", icon = R.drawable.ic_dashboard),
                        NavItem(route = "/stats", icon = R.drawable.ic_stats)
                    )
                )
            }
        }) {
            NavHost(
                navController = navController, startDestination = "/home", modifier = Modifier.padding(it)
            ) {
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

                composable(route = "/dashboard") {
                    bottomBarVisibility = true
                    DashboardScreen(navController)
                }

                composable(route = "/stats") {
                    bottomBarVisibility = true
                    StatsScreen(navController)
                }

                composable(route = "/settings") {
                    bottomBarVisibility = false
                    SettingsScreen(navController, themeViewModel)
                }
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
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    BottomAppBar {
        items.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        painter = painterResource(id = item.icon),
                        contentDescription = null
                    )
                },
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedTextColor = LightPrimary,
                    selectedIconColor = LightPrimary,
                    unselectedTextColor = Color.Gray,
                    unselectedIconColor = Color.Gray
                )
            )
        }
    }
}

