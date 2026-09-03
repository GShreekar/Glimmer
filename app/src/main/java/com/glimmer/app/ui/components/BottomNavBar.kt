package com.glimmer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.glimmer.app.R
import com.glimmer.app.ui.HomeRoute
import com.glimmer.app.ui.AddRoute
import com.glimmer.app.ui.CalendarRoute
import com.glimmer.app.ui.SettingsRoute

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding

/**
 * Navigates to a top-level bottom-nav destination the standard way: [popUpTo] the graph's start
 * destination with `saveState = true` so switching tabs doesn't grow the back stack (previously
 * every tap pushed a new entry — Calendar → Settings → Calendar ×10 needed 10 presses of Back to
 * leave), `launchSingleTop` so re-tapping the current tab doesn't duplicate it, and
 * `restoreState` so a tab's scroll position / state survives switching away and back.
 */
private fun NavController.navigateToTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun BottomNavBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(80.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // hasRoute<T>() matches by the actual route type, not a substring of its serialized
        // name — the old `route?.contains("HomeRoute")` would also match e.g. a hypothetical
        // "HomeRouteDetail" route.
        val isHome = currentDestination?.hierarchy?.any { it.hasRoute<HomeRoute>() } == true
        NavItem(
            icon = Icons.Filled.Home,
            label = stringResource(R.string.nav_home),
            selected = isHome,
            onClick = { navController.navigateToTopLevel(HomeRoute) }
        )

        val isAdd = currentDestination?.hierarchy?.any { it.hasRoute<AddRoute>() } == true
        NavItem(
            icon = Icons.Filled.AddCircle,
            label = stringResource(R.string.nav_add),
            selected = isAdd,
            onClick = { navController.navigateToTopLevel(AddRoute) }
        )

        val isCalendar = currentDestination?.hierarchy?.any { it.hasRoute<CalendarRoute>() } == true
        NavItem(
            icon = Icons.Filled.CalendarMonth,
            label = stringResource(R.string.nav_calendar),
            selected = isCalendar,
            onClick = { navController.navigateToTopLevel(CalendarRoute) }
        )

        val isSettings = currentDestination?.hierarchy?.any { it.hasRoute<SettingsRoute>() } == true
        NavItem(
            icon = Icons.Filled.Settings,
            label = stringResource(R.string.nav_settings),
            selected = isSettings,
            onClick = { navController.navigateToTopLevel(SettingsRoute) }
        )
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val modifier = if (selected) {
        Modifier.neumorphic(isSunken = true, cornerRadius = 12.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
    } else {
        Modifier.neumorphic(isSunken = isPressed, cornerRadius = 12.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
    }

    Box(
        modifier = modifier
            .size(56.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
