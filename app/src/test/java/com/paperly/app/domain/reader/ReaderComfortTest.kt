package com.paperly.app.domain.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderComfortTest {
    @Test
    fun defaultsFollowSystemAndAreOff() {
        val c = ReaderComfort()
        assertNull(c.brightness)
        assertEquals(0f, c.blueLight, 0f)
        assertEquals(false, c.keepAwake)
        assertEquals(false, c.hideBars)
    }

    @Test
    fun clampedKeepsValuesInRange() {
        val c = ReaderComfort(brightness = 5f, blueLight = -1f).clamped()
        assertEquals(1f, c.brightness!!, 0f)
        assertEquals(0f, c.blueLight, 0f)
    }

    @Test
    fun brightnessNeverGoesBlack() {
        assertEquals(MIN_BRIGHTNESS, ReaderComfort(brightness = 0f).clamped().brightness!!, 0f)
    }

    @Test
    fun systemBrightnessStaysNullAfterClamp() {
        assertNull(ReaderComfort(brightness = null).clamped().brightness)
    }
}
