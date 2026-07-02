package com.tv.zhuiju.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tv.zhuiju.ui.screen.category.CategoryScreen
import com.tv.zhuiju.ui.screen.drama.DramaScreen
import com.tv.zhuiju.ui.screen.drama.DramaHistoryScreen
import com.tv.zhuiju.ui.screen.drama.ShortDramaPlayerScreen
import com.tv.zhuiju.ui.screen.home.HistoryScreen
import com.tv.zhuiju.ui.screen.home.HomeScreen
import com.tv.zhuiju.ui.screen.home.SearchScreen
import com.tv.zhuiju.ui.screen.player.PlayerScreen
import com.tv.zhuiju.ui.screen.settings.CacheManagerScreen
import com.tv.zhuiju.ui.screen.settings.SettingsScreen
import com.tv.zhuiju.ui.screen.settings.PlayerSettingsScreen
import com.tv.zhuiju.ui.screen.source.SourceManagementScreen
import com.tv.zhuiju.data.model.VideoCategory
import com.google.gson.Gson
import com.tv.zhuiju.data.model.VideoItem
import android.util.Log

/** 判断视频是否为短剧 */
fun isShortDrama(videoJson: String?): Boolean {
    if (videoJson.isNullOrBlank()) {
        Log.d("isShortDrama", "videoJson is null or blank")
        return false
    }
    return try {
        val item = Gson().fromJson(videoJson, VideoItem::class.java)
        val typeName = item.typeName
        val name = item.name
        val byType = VideoCategory.classify(typeName) == VideoCategory.DRAMA
        // 兜底：如果 typeName 不匹配，检查视频名是否包含短剧关键词
        val byName = !byType && VideoCategory.DRAMA.keywords.any { kw ->
            name.contains(kw, ignoreCase = true)
        }
        Log.d("isShortDrama", "typeName=$typeName name=$name byType=$byType byName=$byName")
        byType || byName
    } catch (e: Exception) {
        Log.e("isShortDrama", "parse error: ${e.message}")
        false
    }
}

@Composable
fun AppNavigation(onPlayerActiveChange: (Boolean) -> Unit = {}) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // 判断是否显示底部导航栏（播放页、搜索页、历史页不显示）
    val showBottomBar = currentDestination?.route?.let { route ->
        BottomNavItem.items.any { it.route == route }
    } ?: true

    // 通知 Activity 当前是否在播放页（用于画中画判断）
    DisposableEffect(currentDestination?.route) {
        val isPlayer = currentDestination?.route?.startsWith("player") == true ||
                currentDestination?.route?.startsWith("dramaPlayer") == true
        onPlayerActiveChange(isPlayer)
        onDispose { }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    BottomNavItem.items.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title
                                )
                            },
                            label = {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
            startDestination = BottomNavItem.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Home.route) {
                HomeScreen(
                    onNavigateToPlayer = { videoId, videoJson ->
                        val encoded = java.net.URLEncoder.encode(videoJson, "UTF-8")
                        val route = if (isShortDrama(videoJson)) {
                            "dramaPlayer/$videoId?videoJson=$encoded"
                        } else {
                            "player/$videoId?videoJson=$encoded"
                        }
                        navController.navigate(route)
                    },
                    onNavigateToSearch = {
                        navController.navigate("search")
                    },
                    onNavigateToHistory = {
                        navController.navigate("history")
                    }
                )
            }
            composable(BottomNavItem.Category.route) {
                CategoryScreen(onNavigateToPlayer = { videoId, videoJson ->
                    val encoded = java.net.URLEncoder.encode(videoJson, "UTF-8")
                    val route = if (isShortDrama(videoJson)) {
                        "dramaPlayer/$videoId?videoJson=$encoded"
                    } else {
                        "player/$videoId?videoJson=$encoded"
                    }
                    navController.navigate(route)
                })
            }
            composable(BottomNavItem.Drama.route) {
                DramaScreen(
                    onNavigateToPlayer = { videoId, videoJson ->
                        val encoded = java.net.URLEncoder.encode(videoJson, "UTF-8")
                        navController.navigate("dramaPlayer/$videoId?videoJson=$encoded")
                    },
                    onNavigateToSearch = {
                        navController.navigate("search")
                    },
                    onNavigateToHistory = {
                        navController.navigate("dramaHistory")
                    }
                )
            }
            composable(BottomNavItem.Settings.route) {
                SettingsScreen(
                    onNavigateToPlayerSettings = {
                        navController.navigate("playerSettings")
                    },
                    onNavigateToSourceManagement = {
                        navController.navigate("sourceManagement")
                    },
                    onNavigateToCacheManager = {
                        navController.navigate("cacheManager")
                    }
                )
            }
            composable("cacheManager") {
                CacheManagerScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("playerSettings") {
                PlayerSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("sourceManagement") {
                SourceManagementScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("search") {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToPlayer = { videoId, videoJson ->
                        val encoded = java.net.URLEncoder.encode(videoJson, "UTF-8")
                        val route = if (isShortDrama(videoJson)) {
                            "dramaPlayer/$videoId?videoJson=$encoded"
                        } else {
                            "player/$videoId?videoJson=$encoded"
                        }
                        navController.navigate(route)
                    }
                )
            }
            composable("history") {
                HistoryScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToPlayer = { videoId, videoJson ->
                        val encoded = java.net.URLEncoder.encode(videoJson, "UTF-8")
                        val route = if (isShortDrama(videoJson)) {
                            "dramaPlayer/$videoId?videoJson=$encoded"
                        } else {
                            "player/$videoId?videoJson=$encoded"
                        }
                        navController.navigate(route)
                    }
                )
            }
            composable("dramaHistory") {
                DramaHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToPlayer = { videoId, videoJson ->
                        val encoded = java.net.URLEncoder.encode(videoJson, "UTF-8")
                        navController.navigate("dramaPlayer/$videoId?videoJson=$encoded")
                    }
                )
            }
            composable(
                route = "player/{videoId}?videoJson={videoJson}",
                arguments = listOf(
                    navArgument("videoId") { type = NavType.StringType; defaultValue = "0" },
                    navArgument("videoJson") { type = NavType.StringType; defaultValue = "" }
                )
            ) {
                PlayerScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "dramaPlayer/{videoId}?videoJson={videoJson}",
                arguments = listOf(
                    navArgument("videoId") { type = NavType.StringType; defaultValue = "0" },
                    navArgument("videoJson") { type = NavType.StringType; defaultValue = "" }
                )
            ) {
                ShortDramaPlayerScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}