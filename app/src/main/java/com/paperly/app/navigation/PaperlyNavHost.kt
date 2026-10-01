package com.paperly.app.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.paperly.app.R
import com.paperly.app.core.ui.adaptive.ScreenSize
import com.paperly.app.core.ui.adaptive.rememberScreenSize
import com.paperly.app.feature.library.LibraryScreen
import com.paperly.app.feature.reader.ReaderRoute
import com.paperly.app.feature.scanner.ScanScreen
import com.paperly.app.feature.settings.SettingsScreen
import com.paperly.app.feature.trash.TrashScreen

object Routes {
    const val LIBRARY = "library"
    const val SCAN = "scan"
    const val SETTINGS = "settings"
    const val TRASH = "trash"
    const val READER = "reader/{documentId}"
    fun reader(documentId: String) = "reader/$documentId"
}

private enum class TopLevel(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    Library(Routes.LIBRARY, R.string.nav_library, Icons.Filled.Home),
    Scan(Routes.SCAN, R.string.nav_scan, Icons.Filled.Add),
    Settings(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
}

private fun NavHostController.navigateTopLevel(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun PaperlyRoot(hasIncomingImport: Boolean = false) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val showNav = currentRoute != Routes.READER && currentRoute != Routes.TRASH // full-screen flows
    val useRail = rememberScreenSize() != ScreenSize.Compact // adaptive: rail on medium/expanded

    ContinueReadingEffect(navController, currentRoute)

    // Open-With/Share import is handled by Library: make sure it is on screen (whatever screen we were on).
    LaunchedEffect(hasIncomingImport, currentRoute) {
        if (hasIncomingImport && currentRoute != null && currentRoute != Routes.LIBRARY) {
            navController.navigateTopLevel(Routes.LIBRARY)
        }
    }

    Scaffold(
        bottomBar = {
            if (showNav && !useRail) {
                NavigationBar {
                    TopLevel.entries.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = { navController.navigateTopLevel(item.route) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.padding(padding).consumeWindowInsets(padding)) {
            if (showNav && useRail) {
                NavigationRail {
                    TopLevel.entries.forEach { item ->
                        NavigationRailItem(
                            selected = currentRoute == item.route,
                            onClick = { navController.navigateTopLevel(item.route) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
            NavHost(
                navController = navController,
                startDestination = Routes.LIBRARY,
                modifier = Modifier.weight(1f),
            ) {
                paperlyDestinations(navController)
            }
        }
    }
}

/** Launcher shortcut "Continue reading": opens the most recently opened document (nothing if none yet). */
@Composable
private fun ContinueReadingEffect(
    navController: NavHostController,
    currentRoute: String?,
    viewModel: ContinueReadingViewModel = hiltViewModel(),
) {
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        if (!pending) return@LaunchedEffect
        val id = viewModel.consumeTarget()
        if (id != null && currentRoute != Routes.READER) navController.navigate(Routes.reader(id))
    }
}

private fun NavGraphBuilder.paperlyDestinations(navController: NavHostController) {
    composable(Routes.LIBRARY) {
        LibraryScreen(
            onOpenReader = { navController.navigate(Routes.reader(it)) },
            onOpenTrash = { navController.navigate(Routes.TRASH) },
        )
    }
    composable(Routes.SCAN) { ScanScreen() }
    composable(Routes.SETTINGS) { SettingsScreen() }
    composable(Routes.TRASH) { TrashScreen(onBack = { navController.popBackStack() }) }
    composable(
        Routes.READER,
        arguments = listOf(navArgument("documentId") { type = NavType.StringType }),
    ) {
        ReaderRoute(onBack = { navController.popBackStack() })
    }
}
