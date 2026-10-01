package com.paperly.app.feature.reader

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem

private const val ID_ANNOTATE = 1
private const val ID_COPY = 2

/** Text-selection menu for the EPUB WebView: Annotate + Copy (a custom callback replaces the system menu). */
internal class EpubSelectionMenu(
    private val annotateLabel: String,
    private val copyLabel: String,
    private val onAnnotate: () -> Unit,
    private val onCopy: () -> Unit,
) : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        menu?.add(Menu.NONE, ID_ANNOTATE, Menu.NONE, annotateLabel)?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu?.add(Menu.NONE, ID_COPY, Menu.NONE, copyLabel)?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        when (item?.itemId) {
            ID_ANNOTATE -> onAnnotate()
            ID_COPY -> onCopy()
            else -> return false
        }
        mode?.finish()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode?) = Unit
}
