package com.glimmer.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.glimmer.app.ui.screens.HomeScreen
import com.glimmer.app.ui.screens.AddBirthdayScreen
import com.glimmer.app.ui.screens.EditBirthdayScreen
import com.glimmer.app.ui.screens.CalendarScreen
import com.glimmer.app.ui.screens.SettingsScreen
import com.glimmer.app.ui.screens.BirthdayDetailScreen
import com.glimmer.app.viewmodel.GlimmerViewModel
import com.glimmer.app.ui.components.BottomNavBar
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable object AddRoute
@Serializable object CalendarRoute
@Serializable object SettingsRoute
@Serializable object NotificationsRoute
@Serializable object ProfileRoute
@Serializable object SyncBackupRoute
@Serializable data class DetailRoute(val id: Int)
@Serializable data class EditRoute(val id: Int)

@Composable
fun GlimmerApp(viewModel: GlimmerViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Show bottom nav only on the 4 top-level screens
    val showBottomBar = currentDestination?.let {
        it.hasRoute<HomeRoute>() || it.hasRoute<AddRoute>() ||
        it.hasRoute<CalendarRoute>() || it.hasRoute<SettingsRoute>()
    } ?: false

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(navController = navController)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToDetail = { id -> navController.navigate(DetailRoute(id)) },
                    onNavigateToAdd = { navController.navigate(AddRoute) },
                    onNavigateToNotifications = { navController.navigate(NotificationsRoute) },
                    onNavigateToProfile = { navController.navigate(ProfileRoute) }
                )
            }
            composable<AddRoute> {
                AddBirthdayScreen(viewModel = viewModel, onNavigateBack = {
                    navController.popBackStack()
                })
            }
            composable<EditRoute> { backStackEntry ->
                val editRoute = backStackEntry.toRoute<EditRoute>()
                EditBirthdayScreen(
                    id = editRoute.id,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<CalendarRoute> {
                CalendarScreen(
                    viewModel = viewModel,
                    onNavigateToDetail = { id -> navController.navigate(DetailRoute(id)) }
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToNotifications = { navController.navigate(NotificationsRoute) },
                    onNavigateToProfile = { navController.navigate(ProfileRoute) },
                    onNavigateToSync = { navController.navigate(SyncBackupRoute) }
                )
            }
            composable<NotificationsRoute> {
                com.glimmer.app.ui.screens.NotificationsSettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<ProfileRoute> {
                com.glimmer.app.ui.screens.ProfileSettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<SyncBackupRoute> {
                com.glimmer.app.ui.screens.SyncBackupScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<DetailRoute> { backStackEntry ->
                val detailRoute = backStackEntry.toRoute<DetailRoute>()
                BirthdayDetailScreen(
                    id = detailRoute.id,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { id -> navController.navigate(EditRoute(id)) }
                )
            }
        }
    }
}
