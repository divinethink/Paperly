package com.paperly.app.domain.cover

/** Pure helpers for the fallback letter cover (unit-tested). */
object CoverLetter {
    fun letter(title: String): String =
        title.trim().codePoints().findFirst().let { if (it.isPresent) String(Character.toChars(it.asInt)).uppercase() else "?" }

    fun colorIndex(title: String, size: Int): Int = Math.floorMod(title.trim().hashCode(), size)
}
