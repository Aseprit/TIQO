package com.aseprit.tiqo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aseprit.tiqo.core.designsystem.component.TiqoBottomDock
import com.aseprit.tiqo.core.designsystem.component.TiqoBackground
import com.aseprit.tiqo.feature.calendar.CalendarRoute
import com.aseprit.tiqo.feature.calendar.CalendarViewModel
import com.aseprit.tiqo.feature.chat.ChatRoute
import com.aseprit.tiqo.feature.chat.ChatViewModel
import com.aseprit.tiqo.feature.home.HomeRoute
import com.aseprit.tiqo.feature.home.HomeViewModel
import com.aseprit.tiqo.feature.models.ModelManagerScreen
import com.aseprit.tiqo.feature.settings.SettingsRoute
import com.aseprit.tiqo.feature.settings.SettingsViewModel
import com.aseprit.tiqo.feature.statistics.StatisticsRoute
import com.aseprit.tiqo.feature.statistics.StatisticsViewModel
import java.time.LocalDate

@Composable
fun TiqoApp(
    container: AppContainer,
    initialDate: LocalDate,
    onRequestNotificationPermission: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    var selectedDate by remember { mutableStateOf(initialDate) }
    val mainRoutes = MainDestination.entries.map { it.route }.toSet()

    TiqoBackground {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            bottomBar = {
                if (currentRoute in mainRoutes) {
                    TiqoBottomBar(
                        currentRoute = currentRoute,
                        onDestinationClick = { destination ->
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { contentPadding ->
            Box(Modifier.padding(contentPadding)) {
                NavHost(navController, startDestination = MainDestination.Home.route) {
                    composable(MainDestination.Calendar.route) {
                        val vm: CalendarViewModel = viewModel(
                            factory = CalendarViewModel.Factory(
                                container.plannerRepository,
                                container.settingsRepository,
                                selectedDate,
                            ),
                        )
                        CalendarRoute(
                            viewModel = vm,
                            selectedDate = selectedDate,
                            onSelectedDateChange = { selectedDate = it },
                            onRequestNotificationPermission = onRequestNotificationPermission,
                        )
                    }
                    composable(MainDestination.Home.route) {
                        val vm: HomeViewModel = viewModel(
                            factory = HomeViewModel.Factory(
                                container.plannerRepository,
                                container.settingsRepository,
                                selectedDate,
                            ),
                        )
                        HomeRoute(
                            viewModel = vm,
                            selectedDate = selectedDate,
                            onSelectedDateChange = { selectedDate = it },
                            onOpenSettings = { navController.navigate(Route.Settings) },
                            onOpenStatistics = { navController.navigate(Route.Statistics) },
                            onRequestNotificationPermission = onRequestNotificationPermission,
                        )
                    }
                    composable(MainDestination.Chat.route) {
                        val vm: ChatViewModel = viewModel(
                            factory = ChatViewModel.Factory(
                                container.chatRepository,
                                container.plannerAgent,
                                container.plannerEngine,
                                container.chatEngine,
                            ),
                        )
                        ChatRoute(vm, container.modelManager, onOpenModels = { navController.navigate(Route.Models) })
                    }
                    composable(Route.Settings) {
                        val vm: SettingsViewModel = viewModel(
                            factory = SettingsViewModel.Factory(container.settingsRepository),
                        )
                        SettingsRoute(
                            viewModel = vm,
                            modelManager = container.modelManager,
                            reminderScheduler = container.reminderScheduler,
                            onBack = navController::popBackStack,
                            onRequestNotificationPermission = onRequestNotificationPermission,
                            onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                            onOpenModels = { navController.navigate(Route.Models) },
                        )
                    }
                    composable(Route.Statistics) {
                        val vm: StatisticsViewModel = viewModel(
                            factory = StatisticsViewModel.Factory(
                                container.plannerRepository,
                                container.settingsRepository,
                            ),
                        )
                        StatisticsRoute(vm, navController::popBackStack)
                    }
                    composable(Route.Models) {
                        ModelManagerScreen(container.modelManager, navController::popBackStack)
                    }
                }
            }
        }
    }
}

@Composable
private fun TiqoBottomBar(currentRoute: String?, onDestinationClick: (MainDestination) -> Unit) {
    TiqoBottomDock(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        MainDestination.entries.forEach { destination ->
            val selected = currentRoute == destination.route
            Surface(
                modifier = Modifier.weight(1f).height(54.dp).clickable { onDestinationClick(destination) },
                shape = RoundedCornerShape(18.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(destination.icon, stringResource(destination.labelRes))
                    if (selected) Text(stringResource(destination.labelRes), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private enum class MainDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    Calendar("calendar", R.string.nav_calendar, Icons.Rounded.CalendarMonth),
    Home("home", R.string.nav_home, Icons.Rounded.Home),
    Chat("chat", R.string.nav_chat, Icons.Rounded.ChatBubbleOutline),
}

private object Route {
    const val Settings = "settings"
    const val Statistics = "statistics"
    const val Models = "models"
}
