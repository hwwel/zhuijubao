package com.tv.zhuiju.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Theaters
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : BottomNavItem(
        route = "home",
        title = "首页",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )

    data object Category : BottomNavItem(
        route = "category",
        title = "分类",
        selectedIcon = Icons.Filled.List,
        unselectedIcon = Icons.Outlined.List
    )

    data object Drama : BottomNavItem(
        route = "drama",
        title = "短剧",
        selectedIcon = Icons.Filled.Theaters,
        unselectedIcon = Icons.Outlined.Theaters
    )

    data object Settings : BottomNavItem(
        route = "settings",
        title = "设置",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )

    companion object {
        val items = listOf(Home, Category, Drama, Settings)
    }
}