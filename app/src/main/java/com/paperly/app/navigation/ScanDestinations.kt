package com.paperly.app.navigation

import androidx.compose.runtime.Composable
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
import com.paperly.app.feature.scanner.ScanScreen

/** Scan tab (scanner opens at once), gallery import (Scanned ⋮ menu), the page grid and the page editor. */
internal fun NavGraphBuilder.scanDestinations(navController: NavHostController) {
    composable(Routes.SCAN) { ScanRoute(navController, ScanMode.Camera) }
    composable(Routes.SCAN_GALLERY) { ScanRoute(navController, ScanMode.Gallery) }
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
        ),
    ) {
        ScanEditScreen(
            // a new scan opens its page grid (Notebloc style); an edit goes back to where it was started
            onDone = { openId ->
                if (openId != null) navController.showLibrary(openId) else navController.popBackStack()
            },
            onCancelAdd = { navController.popBackStack() }, // nothing added: back to the grid
        )
    }
}

@Composable
private fun ScanRoute(navController: NavHostController, mode: ScanMode) {
    ScanScreen(
        mode = mode,
        onSaved = { editId, openId ->
            if (editId != null) {
                navController.navigate(Routes.scanEdit(editId, isNew = true)) // new scan: crop view first
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
