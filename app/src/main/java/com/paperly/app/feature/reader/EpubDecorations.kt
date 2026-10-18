package com.paperly.app.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationStroke
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.EpubAnnotation
import kotlinx.coroutines.awaitCancellation
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.shared.ExperimentalReadiumApi

private const val ANNOTATION_GROUP = "annotations"

/** Highlight/Note -> Readium highlight; Underline/Squiggly/Strikethrough -> [EpubLineStyle] (colour + thickness). */
internal fun EpubAnnotation.toDecoration(): Decoration? {
    val locator = decodeLocator(locatorJson) ?: return null
    val tint = (if (type == AnnotationType.NOTE) AnnotationColor.BLUE else color ?: AnnotationColor.defaultFor(type))
        .argb()
        .toArgb()
    val style = when (type) {
        AnnotationType.HIGHLIGHT, AnnotationType.NOTE -> Decoration.Style.Highlight(tint)
        AnnotationType.UNDERLINE -> EpubLineStyle(EpubLineKind.UNDERLINE, tint, weightDp())
        AnnotationType.SQUIGGLY -> EpubLineStyle(EpubLineKind.SQUIGGLY, tint, weightDp())
        AnnotationType.STRIKETHROUGH -> EpubLineStyle(EpubLineKind.STRIKETHROUGH, tint, weightDp())
    }
    return Decoration(id = id, locator = locator, style = style)
}

private fun EpubAnnotation.weightDp(): Float = (stroke ?: AnnotationStroke.MEDIUM).dp

/** Draws saved annotations in the book and opens the tap popup when one is tapped. */
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
                val anchor = event.rect?.let {
                    EpubAnchor(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat())
                }
                annotations.onTapped(event.decoration.id, anchor)
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
        initialType = controller.style.value.type,
        callbacks = EditorCallbacks({ controller.save(current, it) }, controller::delete, controller::close),
    )
}
