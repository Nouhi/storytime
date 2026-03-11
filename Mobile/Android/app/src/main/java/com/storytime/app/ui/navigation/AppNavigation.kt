package com.storytime.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.storytime.app.R
import com.storytime.app.ui.generate.GenerateScreen
import com.storytime.app.ui.history.HistoryDetailScreen
import com.storytime.app.ui.history.HistoryListScreen
import com.storytime.app.ui.settings.FamilyMembersScreen
import com.storytime.app.ui.settings.SettingsScreen

sealed class Screen(val route: String, @StringRes val labelRes: Int, val icon: ImageVector) {
    data object Create : Screen("create", R.string.tab_create, Icons.Default.AutoAwesome)
    data object History : Screen("history", R.string.tab_history, Icons.Default.History)
    data object Settings : Screen("settings", R.string.tab_settings, Icons.Default.Settings)
}

private val bottomNavItems = listOf(Screen.Create, Screen.History, Screen.Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Only show bottom bar on top-level screens
    val showBottomBar = currentDestination?.route in bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = stringResource(screen.labelRes)) },
                            label = { Text(stringResource(screen.labelRes)) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Create.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Create.route) {
                GenerateScreen()
            }
            composable(Screen.History.route) {
                HistoryListScreen(
                    onStoryClick = { storyId ->
                        navController.navigate("history/$storyId")
                    }
                )
            }
            composable(
                route = "history/{storyId}",
                arguments = listOf(navArgument("storyId") { type = NavType.IntType })
            ) { backStackEntry ->
                val storyId = backStackEntry.arguments?.getInt("storyId") ?: return@composable
                HistoryDetailScreen(
                    storyId = storyId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onFamilyMembersClick = {
                        navController.navigate("family_members")
                    }
                )
            }
            composable("family_members") {
                FamilyMembersScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
