package com.paperly.app.feature.reader

import android.graphics.Bitmap
import android.util.LruCache

/** Byte-bounded LRU of rendered pages. Evicted bitmaps are only dereferenced (Compose may still draw them). */
class PageBitmapCache(maxKb: Int = DEFAULT_MAX_KB) {
    private val cache = object : LruCache<Int, Bitmap>(maxKb) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / BYTES_PER_KB
    }

    fun get(index: Int): Bitmap? = cache.get(index)

    fun put(index: Int, bitmap: Bitmap) {
        cache.put(index, bitmap)
    }

    fun clear() = cache.evictAll()

    private companion object {
        const val DEFAULT_MAX_KB = 48 * 1024
        const val BYTES_PER_KB = 1024
    }
}
