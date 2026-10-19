package com.paperly.app.feature.library

/** What the page grid can do with the pages the user ticked (JPG pictures, except [print] = a PDF of those pages). */
internal class PageExport(
    val share: () -> Unit,
    val email: () -> Unit,
    val print: () -> Unit,
    val gallery: () -> Unit,
    val storage: () -> Unit,
)
