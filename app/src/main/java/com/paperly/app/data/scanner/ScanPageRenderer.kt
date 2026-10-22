package com.paperly.app.data.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import com.paperly.app.domain.scanner.LookFilters
import com.paperly.app.domain.scanner.PixelFilters
import com.paperly.app.domain.scanner.ScanEditPage
import com.paperly.app.domain.scanner.ScanFilter
import com.paperly.app.domain.scanner.ScanQuad
import java.io.File
import java.io.IOException

/** Bitmap work of the page editor (decode, perspective flatten, rotate, filter). Throws on unreadable input. */
internal object ScanPageRenderer {
    private const val MIN_SPAN = 0.1f
    private const val EDGE_BLEED = 1f
    private const val ROTATE_180 = 180
    private const val ROTATE_90 = 90
    private const val ROTATE_270 = 270

    /** Upright (EXIF applied) bitmap whose longest side is >= [targetLongSide] when the file is that large. */
    fun decode(file: File, targetLongSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val long = maxOf(bounds.outWidth, bounds.outHeight)
        if (long <= 0) throw IOException("Unreadable image")
        var sample = 1
        while (long / (sample * 2) >= targetLongSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(file.path, opts) ?: throw IOException("Cannot decode image")
        val degrees = exifDegrees(file)
        if (degrees == 0) return bitmap
        val turned = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply { postRotate(degrees.toFloat()) },
            true,
        )
        if (turned !== bitmap) bitmap.recycle()
        return turned
    }

    private fun exifDegrees(file: File): Int = try {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> ROTATE_90
            ExifInterface.ORIENTATION_ROTATE_180 -> ROTATE_180
            ExifInterface.ORIENTATION_ROTATE_270 -> ROTATE_270
            else -> 0
        }
    } catch (ignored: IOException) {
        0
    }

    /** Flattened, filtered, adjusted, rotated copy of [src] per [page]; [src] is left untouched; side <= [maxSide]. */
    fun render(src: Bitmap, page: ScanEditPage, maxSide: Int): Bitmap {
        val quad = page.quad
        val rotation = page.rotation
        val (w, h) = ScanQuad.outputSize(quad, src.width, src.height, maxSide)
        val flat = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        flat.eraseColor(Color.WHITE)
        val from = FloatArray(POLY_VALUES)
        quad.points.forEachIndexed { i, p ->
            from[i * 2] = p.x * src.width
            from[i * 2 + 1] = p.y * src.height
        }
        // map slightly past the output edge so the edge pixels are never half-blended with transparent black
        val lo = -EDGE_BLEED
        val right = w + EDGE_BLEED
        val bottom = h + EDGE_BLEED
        val to = floatArrayOf(lo, lo, right, lo, right, bottom, lo, bottom)
        val matrix = Matrix()
        matrix.setPolyToPoly(from, 0, to, 0, ScanQuad.CORNERS)
        Canvas(flat).drawBitmap(src, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        applyLook(flat, page) // before rotating: the Bitmap we created is mutable, filters ignore orientation
        if (rotation == 0) return flat
        val turned = Bitmap.createBitmap(
            flat,
            0,
            0,
            w,
            h,
            Matrix().apply { postRotate(rotation.toFloat()) },
            true,
        )
        if (turned !== flat) flat.recycle()
        return turned
    }

    /** Decode + render for the final PDF: only as much of the source is decoded as the output needs. */
    fun renderFile(file: File, page: ScanEditPage, maxSide: Int): Bitmap {
        val quad = page.quad
        val span = maxOf(quad.points.maxOf { it.x } - quad.points.minOf { it.x }, MIN_SPAN)
        val spanY = maxOf(quad.points.maxOf { it.y } - quad.points.minOf { it.y }, MIN_SPAN)
        val src = decode(file, (maxSide / minOf(span, spanY)).toInt())
        try {
            return render(src, page, maxSide)
        } finally {
            src.recycle()
        }
    }

    private fun applyLook(bitmap: Bitmap, page: ScanEditPage) {
        val filter = page.filter
        if (filter == ScanFilter.ORIGINAL && page.brightness == 0 && page.contrast == 0) return
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        val filtered = when (filter) {
            ScanFilter.COLOR -> PixelFilters.enhanceColor(px)
            ScanFilter.GRAY -> PixelFilters.gray(px)
            ScanFilter.BW -> PixelFilters.blackWhite(px, w, h)
            ScanFilter.DOCS -> LookFilters.docs(px, w, h)
            ScanFilter.AUTO -> LookFilters.autoEnhance(px)
            ScanFilter.PHOTO -> LookFilters.photo(px)
            ScanFilter.CLEAN -> LookFilters.clean(px, w, h)
            ScanFilter.SEPIA -> LookFilters.sepia(px, w, h)
            ScanFilter.ORIGINAL -> px
        }
        val out = LookFilters.adjust(filtered, page.brightness, page.contrast)
        bitmap.setPixels(out, 0, w, 0, 0, w, h)
    }

    private const val POLY_VALUES = ScanQuad.CORNERS * 2
}
