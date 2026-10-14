package com.paperly.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController

/** Open-With of a PDF: stage it (Library copy or temporary copy) and open the Reader; no Library import. */
@Composable
internal fun QuickOpenEffect(navController: NavHostController, viewModel: QuickOpenViewModel = hiltViewModel()) {
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        val uri = pending ?: return@LaunchedEffect
        val id = viewModel.stage(uri)
        viewModel.consume(uri)
        if (id != null) navController.navigate(Routes.reader(id))
    }
}
