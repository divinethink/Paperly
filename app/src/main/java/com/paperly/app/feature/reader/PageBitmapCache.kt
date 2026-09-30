package com.paperly.app.feature.reader

import android.graphics.Bitmap
import android.util.LruCache

/** Byte-bounded LRU of rendered pages, keyed by page + render width. Evicted bitmaps are only dereferenced. */
class PageBitmapCache(maxKb: Int = DEFAULT_MAX_KB) {
    private val cache = object : LruCache<Long, Bitmap>(maxKb) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount / BYTES_PER_KB
    }

    fun get(index: Int, widthPx: Int): Bitmap? = cache.get(key(index, widthPx))

    fun put(index: Int, widthPx: Int, bitmap: Bitmap) {
        cache.put(key(index, widthPx), bitmap)
    }

    private fun key(index: Int, widthPx: Int): Long = (index.toLong() shl Int.SIZE_BITS) or widthPx.toLong()

    fun clear() = cache.evictAll()

    private companion object {
        const val DEFAULT_MAX_KB = 48 * 1024
        const val BYTES_PER_KB = 1024
    }
}
