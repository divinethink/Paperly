package com.paperly.app.feature.library

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.paperly.app.R

@Composable
internal fun ColumnScope.LibraryMenuItems(isScanTab: Boolean, navigation: LibraryNavigation, close: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_trash)) },
        onClick = {
            close()
            navigation.onOpenTrash()
        },
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_storage)) },
        onClick = {
            close()
            navigation.onOpenStorage()
        },
    )
    if (isScanTab) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.scan_gallery)) },
            onClick = {
                close()
                navigation.onScanGallery()
            },
        )
    }
}

@Composable
internal fun DuplicateHost(dup: DuplicatePrompt, viewModel: LibraryViewModel, navigation: LibraryNavigation) {
    DuplicateDialog(
        prompt = dup,
        onSkip = viewModel::dismissDuplicate,
        onKeepBoth = viewModel::keepBoth,
        onOpenExisting = {
            viewModel.dismissDuplicate()
            navigation.onOpenReader(dup.existingId)
        },
    )
}

internal fun folderUi(
    state: LibraryUiState,
    viewModel: LibraryViewModel,
    organize: OrganizeViewModel,
    navigation: LibraryNavigation,
    showDialog: (LibraryDialog) -> Unit,
) = FolderUi(
    onSelect = viewModel::selectFolder,
    onNew = { showDialog(LibraryDialog.NewFolder(null)) },
    onRename = { state.openFolder()?.let { showDialog(LibraryDialog.RenameFolder(it)) } },
    onDelete = {
        deleteFolderAction(state, it, organize)?.let(showDialog)
        if (!it) viewModel.selectFolder(state.parentFolderId())
    },
    onScan = navigation.onScan,
    onGallery = navigation.onScanGallery,
)
