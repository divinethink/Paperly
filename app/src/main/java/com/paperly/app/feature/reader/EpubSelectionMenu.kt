package com.paperly.app.feature.reader

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem

/**
 * Only reports "text is selected" (true) / "selection gone" (false). The system menu stays empty, so the selection
 * handles still work while the Compose bar ([EpubSelectionPopup]) is the one visible menu.
 */
internal class EpubSelectionMenu(private val onSelecting: (Boolean) -> Unit) : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        onSelecting(true)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false

    override fun onDestroyActionMode(mode: ActionMode?) = onSelecting(false)
}
