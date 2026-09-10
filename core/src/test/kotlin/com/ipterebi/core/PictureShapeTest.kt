package com.ipterebi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The picture-in-picture window's shape. Android throws for anything outside
 * 1:2.39 to 2.39:1, so the clamp is the part that matters: a wrong answer here
 * is a crash on leaving the app, not an ugly window.
 */
class PictureShapeTest {

    private fun AspectRatio.value() = numerator.toDouble() / denominator

    // What Android checks, exactly: PictureInPictureParams rejects outside these.
    private val narrowest = 100.0 / 239
    private val widest = 239.0 / 100

    @Test
    fun `an ordinary HD picture keeps its shape`() {
        assertEquals(16.0 / 9, pictureInPictureShape(1920, 1080, 1f)!!.value(), 0.001)
    }

    @Test
    fun `an anamorphic picture is shown wide, not as stored`() {
        // PAL widescreen: 720x576 stored, pixels 64:45 wide, 16:9 on screen.
        assertEquals(16.0 / 9, pictureInPictureShape(720, 576, 64f / 45)!!.value(), 0.001)
    }

    @Test
    fun `no size yet means no shape`() {
        assertNull(pictureInPictureShape(0, 0, 1f))
        assertNull(pictureInPictureShape(1920, 0, 1f))
        assertNull(pictureInPictureShape(-1, 1080, 1f))
    }

    @Test
    fun `a nonsense pixel ratio is taken as square pixels`() {
        for (bad in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(16.0 / 9, pictureInPictureShape(1920, 1080, bad)!!.value(), 0.001, "pixel ratio $bad")
        }
    }

    @Test
    fun `too wide is pulled in to what Android accepts`() {
        val shape = pictureInPictureShape(4000, 1000, 1f)!!.value()
        assertTrue(shape <= widest, "$shape is wider than Android allows")
        assertTrue(shape > 2.3)
    }

    @Test
    fun `too tall is pulled in to what Android accepts`() {
        val shape = pictureInPictureShape(1, 1000, 1f)!!.value()
        assertTrue(shape >= narrowest, "$shape is narrower than Android allows")
        assertTrue(shape < 0.45)
    }

    @Test
    fun `nothing anywhere in range escapes the limits after rounding`() {
        for (width in 1..3000 step 7) {
            val shape = pictureInPictureShape(width, 1000, 1f)!!.value()
            assertTrue(shape in narrowest..widest, "$width x 1000 gave $shape")
        }
    }
}
