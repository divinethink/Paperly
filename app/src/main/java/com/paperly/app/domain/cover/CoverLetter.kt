package com.paperly.app.domain.cover

/** Pure helpers for the fallback letter cover (unit-tested). */
object CoverLetter {
    fun letter(title: String): String {
        val first = title.trim().codePoints().findFirst()
        return if (first.isPresent) String(Character.toChars(first.asInt)).uppercase() else "?"
    }

    fun colorIndex(title: String, size: Int): Int = Math.floorMod(title.trim().hashCode(), size)
}
