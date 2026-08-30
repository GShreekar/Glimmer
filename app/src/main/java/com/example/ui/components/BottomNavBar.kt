package com.example.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.ui.HomeRoute
import com.example.ui.AddRoute
import com.example.ui.CalendarRoute
import com.example.ui.SettingsRoute

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding

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
        val isHome = currentDestination?.hierarchy?.any { it.route?.contains("HomeRoute") == true } == true
        NavItem(
            icon = Icons.Filled.Home,
            label = "Home",
            selected = isHome,
            onClick = {
                navController.navigate(HomeRoute) {
                    popUpTo(HomeRoute) { inclusive = true }
                }
            }
        )
        
        val isAdd = currentDestination?.hierarchy?.any { it.route?.contains("AddRoute") == true } == true
        NavItem(
            icon = Icons.Filled.AddCircle,
            label = "Add",
            selected = isAdd,
            onClick = {
                navController.navigate(AddRoute)
            }
        )
        
        val isCalendar = currentDestination?.hierarchy?.any { it.route?.contains("CalendarRoute") == true } == true
        NavItem(
            icon = Icons.Filled.CalendarMonth,
            label = "Calendar",
            selected = isCalendar,
            onClick = {
                navController.navigate(CalendarRoute)
            }
        )
        
        val isSettings = currentDestination?.hierarchy?.any { it.route?.contains("SettingsRoute") == true } == true
        NavItem(
            icon = Icons.Filled.Settings,
            label = "Settings",
            selected = isSettings,
            onClick = {
                navController.navigate(SettingsRoute)
            }
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
