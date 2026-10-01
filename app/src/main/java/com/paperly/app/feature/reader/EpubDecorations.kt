package com.paperly.app.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.EpubAnnotation
import kotlinx.coroutines.awaitCancellation
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.shared.ExperimentalReadiumApi

private const val ANNOTATION_GROUP = "annotations"

/** Highlight/Note -> highlight; Underline/Strikethrough -> underline (Readium has no strikethrough style). */
internal fun EpubAnnotation.toDecoration(): Decoration? {
    val locator = decodeLocator(locatorJson) ?: return null
    val tint = (if (type == AnnotationType.NOTE) AnnotationColor.BLUE else color ?: AnnotationColor.defaultFor(type))
        .argb()
        .toArgb()
    val style = when (type) {
        AnnotationType.HIGHLIGHT, AnnotationType.NOTE -> Decoration.Style.Highlight(tint)
        AnnotationType.UNDERLINE, AnnotationType.STRIKETHROUGH -> Decoration.Style.Underline(tint)
    }
    return Decoration(id = id, locator = locator, style = style)
}

/** Draws saved annotations in the book and opens the edit dialog when one is tapped. */
@OptIn(ExperimentalReadiumApi::class)
@Composable
internal fun EpubDecorationEffect(annotations: EpubAnnotationController) {
    val activity = LocalContext.current as FragmentActivity
    val items by annotations.items.collectAsStateWithLifecycle()
    LaunchedEffect(items) {
        EpubFragmentHost.awaitNavigator(activity)
            .applyDecorations(items.mapNotNull { it.toDecoration() }, ANNOTATION_GROUP)
    }
    LaunchedEffect(annotations) {
        val navigator = EpubFragmentHost.awaitNavigator(activity)
        val listener = object : DecorableNavigator.Listener {
            override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                annotations.startEdit(event.decoration.id)
                return true
            }
        }
        navigator.addDecorationListener(ANNOTATION_GROUP, listener)
        try {
            awaitCancellation()
        } finally {
            navigator.removeDecorationListener(listener)
        }
    }
}

@Composable
internal fun EpubAnnotationHost(editor: EpubAnnotationEditor?, controller: EpubAnnotationController) {
    val current = editor ?: return
    AnnotationEditorDialog(
        key = current,
        existing = current.existing?.toDialogModel(),
        onSave = { controller.save(current, it) },
        onDelete = controller::delete,
        onClose = controller::close,
    )
}
