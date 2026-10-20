package com.paperly.app.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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
import com.paperly.app.domain.document.LibraryType
import com.paperly.app.feature.library.LIBRARY_TYPE_ARG
import com.paperly.app.feature.library.LibraryNavigation
import com.paperly.app.feature.library.LibraryScreen
import com.paperly.app.feature.reader.ReaderRoute
import com.paperly.app.feature.scanner.LocalAddScanPages
import com.paperly.app.feature.scanner.LocalOpenScanEditor
import com.paperly.app.feature.scanner.LocalOpenScanEditorAt
import com.paperly.app.feature.scanner.LocalOpenScanPages
import com.paperly.app.feature.scanner.ScanEditStart
import com.paperly.app.feature.settings.SettingsScreen
import com.paperly.app.feature.storage.StorageScreen
import com.paperly.app.feature.trash.TrashScreen

/** The Reader handles the system bars itself (edge-to-edge, bars float over the page). */
private val NoInsets = WindowInsets(0)
private val FullScreenRoutes = setOf(Routes.READER, Routes.TRASH, Routes.STORAGE, Routes.SCAN_EDIT, Routes.SCAN_PAGES)

/** Full-screen routes whose teal bar paints the status bar (and which pad the navigation bar themselves). */
private val SelfInsetRoutes =
    setOf(Routes.READER, Routes.SCAN_PAGES, Routes.SCAN_EDIT, Routes.TRASH, Routes.STORAGE)

object Routes {
    private const val LIBRARY_PREFIX = "library_"
    const val SCAN = "scan"
    const val SCAN_GALLERY = "scan-gallery"
    const val SETTINGS = "settings"
    const val TRASH = "trash"
    const val STORAGE = "storage"
    const val READER = "reader/{documentId}"
    fun reader(documentId: String) = "reader/$documentId"
    const val SCAN_PAGES = "scan-pages/{documentId}"
    fun scanPages(documentId: String) = "scan-pages/$documentId"
    const val SCAN_EDIT =
        "scan-edit/{documentId}?new={new}&add={add}&pick={pick}&page={page}&crop={crop}&delete={delete}&cam={cam}"

    /**
     * [isNew] = scan just saved (crop mode first, leaving opens the document); [add] = start "Add scan";
     * [pick] = start the gallery picker ("Add pictures"); [fromCamera] = the new scan came from the in-app camera.
     */
    fun scanEdit(
        documentId: String,
        isNew: Boolean = false,
        add: Boolean = false,
        pick: Boolean = false,
        fromCamera: Boolean = false,
    ) = "scan-edit/$documentId?new=$isNew&add=$add&pick=$pick&cam=$fromCamera"

    /** The editor opened on [page] (0-based): [crop] starts in the crop step, [delete] removes the page at once. */
    fun scanEditAt(documentId: String, page: Int, crop: Boolean = false, delete: Boolean = false) =
        "scan-edit/$documentId?page=$page&crop=$crop&delete=$delete"

    /** One Library destination per tab (Scanned / PDF / EPUB): own back-stack entry, view model and saved state. */
    fun library(type: LibraryType) = LIBRARY_PREFIX + type.key

    fun isLibrary(route: String?) = route?.startsWith(LIBRARY_PREFIX) == true
}

private enum class TopLevel(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    Scanned(Routes.library(LibraryType.SCAN), R.string.nav_scanned, ScannedIcon),
    Pdf(Routes.library(LibraryType.PDF), R.string.nav_pdf, PdfIcon),
    Scan(Routes.SCAN, R.string.nav_scan, Icons.Filled.Add),
    Epub(Routes.library(LibraryType.EPUB), R.string.nav_epub, EpubIcon),
    Settings(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
}

/** Scan is a one-shot flow (scanner -> editor -> Reader): never restore its old stack, or a stale document reopens. */
internal fun NavHostController.navigateTopLevel(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = route != Routes.SCAN && route != Routes.SCAN_GALLERY
}

/** Reader, the page grid, Trash/Storage and (phone) Library/Settings draw under the status bar themselves. */
@Composable
private fun rootInsets(route: String?, useRail: Boolean): WindowInsets {
    val phoneTealTab = !useRail && (Routes.isLibrary(route) || route == Routes.SETTINGS)
    return if (route in SelfInsetRoutes || phoneTealTab) NoInsets else ScaffoldDefaults.contentWindowInsets
}

@Composable
private fun PaperlyBottomBar(currentRoute: String?, navController: NavHostController) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        val itemColors = barItemColors()
        TopLevel.entries.forEach { item ->
            val selected = currentRoute == item.route
            val go = { navController.navigateTopLevel(item.route) }
            if (item == TopLevel.Scan) {
                ScanNavItem(selected, stringResource(item.label), go)
            } else {
                NavigationBarItem(
                    selected = selected,
                    onClick = go,
                    icon = { Icon(item.icon, contentDescription = null) },
                    label = { Text(stringResource(item.label)) },
                    colors = itemColors,
                )
            }
        }
    }
}

@Composable
fun PaperlyRoot(hasIncomingImport: Boolean = false) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val showNav = currentRoute !in FullScreenRoutes // full-screen flows
    val useRail = rememberScreenSize() != ScreenSize.Compact // adaptive: rail on medium/expanded

    ContinueReadingEffect(navController, currentRoute)
    QuickOpenEffect(navController)
    TealStatusBarEffect(currentRoute)

    // Open-With/Share import is handled by Library: make sure it is on screen (whatever screen we were on).
    LaunchedEffect(hasIncomingImport, currentRoute) {
        if (hasIncomingImport && currentRoute != null && !Routes.isLibrary(currentRoute)) {
            navController.navigateTopLevel(Routes.library(LibraryType.PDF))
        }
    }

    Scaffold(
        contentWindowInsets = rootInsets(currentRoute, useRail),
        bottomBar = { if (showNav && !useRail) PaperlyBottomBar(currentRoute, navController) },
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
                startDestination = Routes.library(LibraryType.SCAN), // the app always opens on Scanned
                modifier = Modifier.weight(1f),
            ) {
                paperlyDestinations(navController)
            }
        }
    }
}

/** Cream bar: muted labels/icons, selected item gets a tint pill. */
@Composable
private fun barItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
)

/** The middle Scan destination: a round filled button instead of a plain nav item. */
@Composable
private fun RowScope.ScanNavItem(selected: Boolean, label: String, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 4.dp,
            modifier = Modifier.size(52.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, contentDescription = null) }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
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
    LibraryType.entries.forEach { libraryType ->
        composable(
            Routes.library(libraryType),
            arguments = listOf(
                navArgument(LIBRARY_TYPE_ARG) {
                    type = NavType.StringType
                    defaultValue = libraryType.key
                },
            ),
        ) {
            val addPages: (String) -> Unit = { navController.navigate(Routes.scanEdit(it, add = true)) }
            val openPages: (String) -> Unit = { navController.navigate(Routes.scanPages(it)) }
            CompositionLocalProvider(LocalAddScanPages provides addPages, LocalOpenScanPages provides openPages) {
                LibraryScreen(
                    LibraryNavigation(
                        onOpenReader = { navController.navigate(Routes.reader(it)) },
                        onOpenTrash = { navController.navigate(Routes.TRASH) },
                        onOpenStorage = { navController.navigate(Routes.STORAGE) },
                        onShowTab = { navController.navigateTopLevel(Routes.library(it)) },
                        onScanGallery = { navController.navigate(Routes.SCAN_GALLERY) },
                        onScan = { navController.navigateTopLevel(Routes.SCAN) },
                    ),
                )
            }
        }
    }
    scanDestinations(navController)
    composable(Routes.SETTINGS) { SettingsScreen() }
    composable(Routes.TRASH) { TrashScreen(onBack = { navController.popBackStack() }) }
    composable(Routes.STORAGE) { StorageScreen(onBack = { navController.popBackStack() }) }
    composable(
        Routes.READER,
        arguments = listOf(navArgument("documentId") { type = NavType.StringType }),
    ) {
        // Editing replaces the file, so the Reader (which holds it open) is popped first.
        val openEditor: (String) -> Unit = { id ->
            navController.navigate(Routes.scanEdit(id)) { popUpTo(Routes.READER) { inclusive = true } }
        }
        val openEditorAt: (String, Int, ScanEditStart) -> Unit = { id, page, start ->
            val crop = start == ScanEditStart.CROP
            val route = Routes.scanEditAt(id, page, crop = crop, delete = start == ScanEditStart.DELETE)
            navController.navigate(route) { popUpTo(Routes.READER) { inclusive = true } }
        }
        CompositionLocalProvider(LocalOpenScanEditor provides openEditor, LocalOpenScanEditorAt provides openEditorAt) {
            ReaderRoute(onBack = { navController.popBackStack() })
        }
    }
}
