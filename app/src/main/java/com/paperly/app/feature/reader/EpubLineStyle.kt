package com.paperly.app.feature.reader

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.html.HtmlDecorationTemplate
import org.readium.r2.navigator.html.HtmlDecorationTemplates
import org.readium.r2.shared.ExperimentalReadiumApi

private const val RGB_MASK = 0xFFFFFF
private const val WAVE_W = 6
private const val WAVE_H = 4

/** The look of an underline-family mark in the book. */
internal enum class EpubLineKind { UNDERLINE, SQUIGGLY, STRIKETHROUGH }

/**
 * Readium has only a fixed-weight underline, so line marks (underline, squiggly, strikethrough) get this custom style:
 * colour [tint] (ARGB) and thickness [weightDp]. Drawn by [epubDecorationTemplates].
 */
internal data class EpubLineStyle(val kind: EpubLineKind, val tint: Int, val weightDp: Float) :
    Decoration.Style,
    Parcelable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(kind.name)
        dest.writeInt(tint)
        dest.writeFloat(weightDp)
    }

    companion object CREATOR : Parcelable.Creator<EpubLineStyle> {
        override fun createFromParcel(source: Parcel): EpubLineStyle = EpubLineStyle(
            EpubLineKind.valueOf(source.readString() ?: EpubLineKind.UNDERLINE.name),
            source.readInt(),
            source.readFloat(),
        )

        override fun newArray(size: Int): Array<EpubLineStyle?> = arrayOfNulls(size)
    }
}

private fun hexOf(argb: Int) = "#%06X".format(argb and RGB_MASK)

/** One line of CSS (inline, `!important` as Readium's own templates do) for the box Readium puts on each text line. */
internal fun epubLineCss(style: EpubLineStyle): String {
    val hex = hexOf(style.tint)
    val weight = style.weightDp
    val body = when (style.kind) {
        EpubLineKind.UNDERLINE -> "border-bottom: ${weight}px solid $hex !important;"
        EpubLineKind.STRIKETHROUGH ->
            "background: linear-gradient($hex, $hex) no-repeat center / 100% ${weight}px !important;"
        EpubLineKind.SQUIGGLY -> {
            val svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$WAVE_W\" height=\"$WAVE_H\">" +
                "<path d=\"M0 3 Q1.5 0 3 3 T6 3\" fill=\"none\" stroke=\"$hex\" stroke-width=\"$weight\"/></svg>"
            "background: url(data:image/svg+xml,${Uri.encode(svg)}) repeat-x left bottom !important;"
        }
    }
    return "box-sizing: border-box !important; $body"
}

/** Readium's default templates plus the one for [EpubLineStyle]; passed to the navigator configuration. */
@OptIn(ExperimentalReadiumApi::class)
internal fun epubDecorationTemplates(): HtmlDecorationTemplates {
    val templates = HtmlDecorationTemplates.defaultTemplates()
    templates.set(
        EpubLineStyle::class,
        HtmlDecorationTemplate(
            layout = HtmlDecorationTemplate.Layout.BOXES,
            width = HtmlDecorationTemplate.Width.WRAP,
            element = { decoration ->
                val style = decoration.style as? EpubLineStyle
                "<div style=\"${style?.let(::epubLineCss).orEmpty()}\"></div>"
            },
        ),
    )
    return templates
}
