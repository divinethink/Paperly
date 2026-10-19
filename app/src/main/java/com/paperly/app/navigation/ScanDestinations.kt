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
import com.paperly.app.feature.scanner.ScanScreen

/** Scan tab (scanner opens at once), gallery import (Scanned ⋮ menu) and the page editor. */
internal fun NavGraphBuilder.scanDestinations(navController: NavHostController) {
    composable(Routes.SCAN) { ScanRoute(navController, ScanMode.Camera) }
    composable(Routes.SCAN_GALLERY) { ScanRoute(navController, ScanMode.Gallery) }
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
        ),
    ) {
        ScanEditScreen(onDone = { openId -> navController.showLibrary(openId) })
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

/** Back to the Scanned tab; [openDocumentId] (a new scan) then opens in the Reader on top of it. */
private fun NavHostController.showLibrary(openDocumentId: String?) {
    navigateTopLevel(Routes.library(LibraryType.SCAN))
    if (openDocumentId != null) navigate(Routes.reader(openDocumentId))
}
