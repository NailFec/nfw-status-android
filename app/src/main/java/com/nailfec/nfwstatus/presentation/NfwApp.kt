package com.nailfec.nfwstatus.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nailfec.nfwstatus.R
import com.nailfec.nfwstatus.presentation.calendars.CalendarAddIcon
import com.nailfec.nfwstatus.presentation.calendars.CalendarsScreen
import com.nailfec.nfwstatus.presentation.meals.MealsScreen
import com.nailfec.nfwstatus.presentation.settings.SettingsScreen
import com.nailfec.nfwstatus.presentation.sleep.SleepScreen
import com.nailfec.nfwstatus.presentation.status.StatusScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfwApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onMessage: (String) -> Unit = remember(snackbarHostState, scope) {
        { message: String ->
            scope.launch { snackbarHostState.showSnackbar(message) }
            Unit
        }
    }
    val refreshAction = remember { mutableStateOf<() -> Unit>({}) }
    val addAction = remember { mutableStateOf<() -> Unit>({}) }
    val onBindRefresh = remember {
        { action: () -> Unit -> refreshAction.value = action }
    }
    val onBindAdd = remember {
        { action: () -> Unit -> addAction.value = action }
    }
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    val showSettings = destination?.hasRoute<SettingsRoute>() == true
    val showCalendars = destination?.hasRoute<CalendarsRoute>() == true
    val titleRes = when {
        destination?.hasRoute<MealsRoute>() == true -> R.string.nav_meals
        destination?.hasRoute<SleepRoute>() == true -> R.string.nav_sleep
        showCalendars -> R.string.nav_calendars
        showSettings -> R.string.title_settings
        else -> R.string.nav_status
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = {
                    if (showSettings) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = {
                    if (!showSettings) {
                        if (showCalendars) {
                            IconButton(onClick = { addAction.value.invoke() }) {
                                CalendarAddIcon()
                            }
                        }
                        IconButton(onClick = { refreshAction.value.invoke() }) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.action_refresh),
                            )
                        }
                        IconButton(onClick = {
                            navController.navigate(SettingsRoute) { launchSingleTop = true }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.action_settings),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!showSettings) {
                NavigationBar {
                    NavItem(
                        selected = destination?.hasRoute<StatusRoute>() != false &&
                            destination?.hasRoute<MealsRoute>() != true &&
                            destination?.hasRoute<SleepRoute>() != true &&
                            destination?.hasRoute<CalendarsRoute>() != true,
                        labelRes = R.string.nav_status,
                        selectedIcon = Icons.Filled.Schedule,
                        unselectedIcon = Icons.Outlined.Schedule,
                        onClick = { navController.openTab(StatusRoute) },
                    )
                    NavItem(
                        selected = destination?.hasRoute<MealsRoute>() == true,
                        labelRes = R.string.nav_meals,
                        selectedIcon = Icons.Filled.Restaurant,
                        unselectedIcon = Icons.Outlined.Restaurant,
                        onClick = { navController.openTab(MealsRoute) },
                    )
                    NavItem(
                        selected = destination?.hasRoute<SleepRoute>() == true,
                        labelRes = R.string.nav_sleep,
                        selectedIcon = Icons.Filled.Bedtime,
                        unselectedIcon = Icons.Outlined.Bedtime,
                        onClick = { navController.openTab(SleepRoute) },
                    )
                    NavItem(
                        selected = showCalendars,
                        labelRes = R.string.nav_calendars,
                        selectedIcon = Icons.Filled.CalendarMonth,
                        unselectedIcon = Icons.Outlined.CalendarMonth,
                        onClick = { navController.openTab(CalendarsRoute) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = StatusRoute,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable<StatusRoute> {
                StatusScreen(onMessage = onMessage, onBindRefresh = onBindRefresh)
            }
            composable<MealsRoute> {
                MealsScreen(onMessage = onMessage, onBindRefresh = onBindRefresh)
            }
            composable<SleepRoute> {
                SleepScreen(onMessage = onMessage, onBindRefresh = onBindRefresh)
            }
            composable<CalendarsRoute> {
                CalendarsScreen(
                    onMessage = onMessage,
                    onBindRefresh = onBindRefresh,
                    onBindAdd = onBindAdd,
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(onMessage = onMessage)
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    selected: Boolean,
    labelRes: Int,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = if (selected) selectedIcon else unselectedIcon,
                contentDescription = null,
            )
        },
        label = { Text(stringResource(labelRes)) },
    )
}

private fun NavHostController.openTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
