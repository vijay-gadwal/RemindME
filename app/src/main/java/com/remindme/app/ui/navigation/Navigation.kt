package com.remindme.app.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.remindme.app.ui.screens.*
import com.remindme.app.viewmodel.ChatViewModel
import com.remindme.app.viewmodel.GoalViewModel
import com.remindme.app.viewmodel.TaskViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Chat : Screen("chat", "Chat", Icons.Default.Chat)
    data object Tasks : Screen("tasks", "Tasks", Icons.Default.Checklist)
    data object Goals : Screen("goals", "Goals", Icons.Default.Flag)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

sealed class DetailScreen(val route: String) {
    data object GoalDetail : DetailScreen("goal_detail/{goalId}")
    data object AddTask : DetailScreen("add_task")
    data object AddGoal : DetailScreen("add_goal")
}

val bottomNavItems = listOf(Screen.Chat, Screen.Tasks, Screen.Goals, Screen.Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    isVoiceMode: Boolean,
    onToggleMode: () -> Unit
) {
    val navController = rememberNavController()
    val chatViewModel: ChatViewModel = viewModel()
    val taskViewModel: TaskViewModel = viewModel()
    val goalViewModel: GoalViewModel = viewModel()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Chat.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Chat.route) {
                ChatScreen(
                    viewModel = chatViewModel,
                    isVoiceMode = isVoiceMode,
                    onToggleMode = onToggleMode
                )
            }
            composable(Screen.Tasks.route) {
                TaskListScreen(
                    viewModel = taskViewModel,
                    onNavigateToAddTask = {
                        navController.navigate(DetailScreen.AddTask.route)
                    }
                )
            }
            composable(Screen.Goals.route) {
                GoalListScreen(
                    viewModel = goalViewModel,
                    onNavigateToGoalDetail = { goalId ->
                        navController.navigate("goal_detail/$goalId")
                    },
                    onNavigateToAddGoal = {
                        navController.navigate(DetailScreen.AddGoal.route)
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
            composable(
                route = DetailScreen.GoalDetail.route,
                arguments = listOf(navArgument("goalId") { type = NavType.LongType })
            ) { backStackEntry ->
                val goalId = backStackEntry.arguments?.getLong("goalId") ?: return@composable
                GoalDetailScreen(
                    goalId = goalId,
                    viewModel = goalViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(DetailScreen.AddTask.route) {
                AddTaskScreen(
                    viewModel = taskViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(DetailScreen.AddGoal.route) {
                AddGoalScreen(
                    viewModel = goalViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
