package com.paperly.app.domain.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextReadabilityTest {
    @Test
    fun englishAndNormalAccentedTextIsReadable() {
        assertTrue(TextReadability.isReadable("The committee published its annual report on economic growth today."))
        assertTrue(
            TextReadability.isReadable(
                "Le gouvernement a annoncé hier une série de mesures économiques destinées à soutenir les entreprises.",
            ),
        )
    }

    @Test
    fun emptyOrTinyTextIsNotReadable() {
        assertFalse(TextReadability.isReadable(""))
        assertFalse(TextReadability.isReadable("  \n Page 1 "))
    }

    @Test
    fun bijoyEncodedTextIsNotReadable() {
        assertFalse(TextReadability.isReadable("†iwR÷vW© wbev©PK‡ÿÎ †fvUvi ZvwjKv Ô‡gqvÙ Ges ‡`kxq A_©bxwZ"))
    }

    @Test
    fun unicodeBengaliIsReadableAndFlagged() {
        val text = "বাংলাদেশ একটি সুন্দর দেশ এখানে অনেক নদী আছে"
        assertTrue(TextReadability.isReadable(text))
        assertTrue(TextReadability.hasBengali(text))
        assertFalse(TextReadability.hasBengali("plain english text"))
    }
}
