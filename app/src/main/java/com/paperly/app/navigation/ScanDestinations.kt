package com.paperly.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.paperly.app.domain.document.LibraryType
import com.paperly.app.feature.scanner.ScanEditScreen
import com.paperly.app.feature.scanner.ScanMode
import com.paperly.app.feature.scanner.ScanPagesActions
import com.paperly.app.feature.scanner.ScanPagesScreen
import com.paperly.app.feature.scanner.ScanRetake
import com.paperly.app.feature.scanner.ScanScreen

/** Scan tab (scanner opens at once), gallery import (Scanned ⋮ menu), the page grid and the page editor. */
internal fun NavGraphBuilder.scanDestinations(navController: NavHostController) {
    composable(Routes.SCAN) { ScanRoute(navController, ScanMode.Camera, it) }
    composable(Routes.SCAN_GALLERY) { ScanRoute(navController, ScanMode.Gallery, it) }
    composable(
        Routes.SCAN_PAGES,
        arguments = listOf(navArgument("documentId") { type = NavType.StringType }),
    ) {
        ScanPagesScreen(
            onBack = { navController.popBackStack() },
            actions = ScanPagesActions(
                onOpenReader = { navController.navigate(Routes.reader(it)) },
                onAddScan = { navController.navigate(Routes.scanEdit(it, add = true)) },
                onAddPictures = { navController.navigate(Routes.scanEdit(it, pick = true)) },
            ),
        )
    }
    scanEditDestination(navController)
}

private fun NavGraphBuilder.scanEditDestination(navController: NavHostController) {
    composable(
        Routes.SCAN_EDIT,
        arguments = listOf(
            navArgument("documentId") { type = NavType.StringType },
            navArgument("new") {
                type = NavType.BoolType
                defaultValue = false
            },
            navArgument("add") {
                type = NavType.BoolType
                defaultValue = false
            },
            navArgument("pick") {
                type = NavType.BoolType
                defaultValue = false
            },
            navArgument("page") {
                type = NavType.IntType
                defaultValue = -1
            },
            navArgument("crop") {
                type = NavType.BoolType
                defaultValue = false
            },
            navArgument("delete") {
                type = NavType.BoolType
                defaultValue = false
            },
            navArgument("cam") {
                type = NavType.BoolType
                defaultValue = false
            },
        ),
    ) {
        ScanEditScreen(
            // a new scan opens its page grid (Notebloc style); an edit goes back to where it was started
            onDone = { openId ->
                if (openId != null) navController.showLibrary(openId) else navController.popBackStack()
            },
            onCancelAdd = { navController.popBackStack() }, // nothing added: back to the grid
            onDiscarded = { retake ->
                // the scan is gone: back to the Scan screen, which reopens the camera for a retake
                navController.previousBackStackEntry?.savedStateHandle?.set(RETAKE_KEY, retake)
                navController.popBackStack()
            },
        )
    }
}

private const val RETAKE_KEY = "scan_retake"

@Composable
private fun ScanRoute(navController: NavHostController, mode: ScanMode, entry: NavBackStackEntry) {
    val retake by entry.savedStateHandle.getStateFlow(RETAKE_KEY, false).collectAsStateWithLifecycle()
    ScanScreen(
        mode = mode,
        retake = ScanRetake(retake) { entry.savedStateHandle[RETAKE_KEY] = false },
        onSaved = { editId, openId, fromCamera ->
            if (editId != null) {
                // new scan: crop view first
                navController.navigate(Routes.scanEdit(editId, isNew = true, fromCamera = fromCamera))
            } else {
                navController.showLibrary(openId)
            }
        },
        onCancel = { navController.showLibrary(null) },
    )
}

/** Back to the Scanned tab; [openDocumentId] (a new scan) then opens its page grid on top of it. */
private fun NavHostController.showLibrary(openDocumentId: String?) {
    navigateTopLevel(Routes.library(LibraryType.SCAN))
    if (openDocumentId != null) navigate(Routes.scanPages(openDocumentId))
}
