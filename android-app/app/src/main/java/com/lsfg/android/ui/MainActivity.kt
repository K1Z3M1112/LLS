package com.lsfg.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lsfg.android.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LsfgTheme {
                MainScreen()
            }
        }
    }
}

sealed class NavRoute(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : NavRoute("dashboard", "Home", Icons.Default.Home)
    data object Settings  : NavRoute("settings",  "Settings", Icons.Default.Tune)
    data object Benchmark : NavRoute("benchmark", "Benchmark", Icons.Default.Speed)
    data object Setup     : NavRoute("setup",     "Setup", Icons.Default.Build)
}

val bottomNavRoutes = listOf(
    NavRoute.Dashboard,
    NavRoute.Settings,
    NavRoute.Benchmark,
    NavRoute.Setup,
)

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        containerColor = SpaceBlack,
        bottomBar = {
            GamingBottomBar(
                routes = bottomNavRoutes,
                currentDestination = currentDestination,
                onNavigate = { route ->
                    navController.navigate(route.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { innerPadding ->
        MainNavGraph(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
fun GamingBottomBar(
    routes: List<NavRoute>,
    currentDestination: androidx.navigation.NavDestination?,
    onNavigate: (NavRoute) -> Unit,
) {
    NavigationBar(
        containerColor = SpaceNavy,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = SpaceBorder,
                shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
            ),
    ) {
        routes.forEach { route ->
            val selected = currentDestination?.hierarchy?.any { it.route == route.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(route) },
                icon = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (selected) {
                            // Red glow indicator above icon
                            Box(
                                modifier = Modifier
                                    .width(24.dp)
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(RedCore),
                            )
                            Spacer(Modifier.height(4.dp))
                        } else {
                            Spacer(Modifier.height(6.dp))
                        }
                        Icon(
                            imageVector = route.icon,
                            contentDescription = route.label,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                label = {
                    Text(
                        text = route.label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = RedCore,
                    selectedTextColor = RedCore,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted,
                    indicatorColor = RedAlpha15,
                ),
            )
        }
    }
}
