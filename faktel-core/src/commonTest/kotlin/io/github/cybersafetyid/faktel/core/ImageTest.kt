package io.github.cybersafetyid.faktel.core

import io.github.cybersafetyid.faktel.core.geometry.Point
import io.github.cybersafetyid.faktel.core.geometry.Quad
import io.github.cybersafetyid.faktel.core.geometry.Rect
import io.github.cybersafetyid.faktel.core.image.RgbImage
import io.github.cybersafetyid.faktel.core.image.warpPerspective
import io.github.cybersafetyid.faktel.core.inference.ChannelOrder
import io.github.cybersafetyid.faktel.core.inference.letterbox
import io.github.cybersafetyid.faktel.core.inference.toTensor
import io.github.cybersafetyid.faktel.core.quality.blurScore
import io.github.cybersafetyid.faktel.core.quality.meanBrightness
import io.github.cybersafetyid.faktel.core.quality.saturatedRatio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ImageTest {
    private val gradient = image(4, 2) { x, y -> (x * 40 shl 16) or (y * 100 shl 8) or 7 }

    @Test
    fun rejectsWrongBufferSize() {
        assertFailsWith<IllegalArgumentException> { RgbImage(2, 2, ByteArray(5)) }
    }

    @Test
    fun rotateClockwiseMovesTopLeftToTopRight() {
        val r = gradient.rotate(90)
        assertEquals(2, r.width)
        assertEquals(4, r.height)
        // original (0,0) -> (height-1-0, 0) = (1, 0)
        assertEquals(gradient.r(0, 0), r.r(1, 0))
        assertEquals(gradient.g(3, 1), r.g(0, 3))
        val back = gradient.rotate(90).rotate(270)
        assertTrue(gradient.data.contentEquals(back.data))
        assertTrue(gradient.data.contentEquals(gradient.rotate(180).rotate(180).data))
    }

    @Test
    fun flipTwiceIsIdentity() {
        assertTrue(gradient.data.contentEquals(gradient.flipHorizontal().flipHorizontal().data))
        assertEquals(gradient.r(3, 0), gradient.flipHorizontal().r(0, 0))
    }

    @Test
    fun cropAndResize() {
        val c = gradient.crop(Rect(1.0, 0.0, 3.0, 2.0))
        assertEquals(2, c.width)
        assertEquals(gradient.r(1, 0), c.r(0, 0))
        val flat = image(10, 10) { _, _ -> gray(90) }
        val r = flat.resize(3, 7)
        assertEquals(90, r.r(1, 3))
        assertTrue(flat === flat.resize(10, 10))
    }

    @Test
    fun rgbaAndBgraOrderingWithStride() {
        // 1x2 image, stride 8 (4 bytes padding per row)
        val rgba = byteArrayOf(1, 2, 3, 9, 0, 0, 0, 0, 4, 5, 6, 9)
        val i = RgbImage.fromRgba(1, 2, rgba, rowStride = 8)
        assertEquals(listOf(1, 2, 3), listOf(i.r(0, 0), i.g(0, 0), i.b(0, 0)))
        assertEquals(listOf(4, 5, 6), listOf(i.r(0, 1), i.g(0, 1), i.b(0, 1)))
        val j = RgbImage.fromBgra(1, 1, byteArrayOf(3, 2, 1, 127))
        assertEquals(listOf(1, 2, 3), listOf(j.r(0, 0), j.g(0, 0), j.b(0, 0)))
    }

    @Test
    fun nv21NeutralChromaGivesGray() {
        val w = 4
        val h = 2
        val buf = ByteArray(w * h + w * h / 2)
        for (i in 0 until w * h) buf[i] = 100.toByte()
        for (i in w * h until buf.size) buf[i] = 128.toByte()
        val img = RgbImage.fromNv21(w, h, buf)
        assertEquals(100, img.r(2, 1))
        assertEquals(100, img.g(2, 1))
        assertEquals(100, img.b(2, 1))
        // Strongly red chroma (V high) must push red up and green/blue down.
        for (i in w * h until buf.size step 2) buf[i] = 255.toByte()
        val red = RgbImage.fromNv21(w, h, buf)
        assertTrue(red.r(0, 0) > 200 && red.g(0, 0) < 100)
    }

    @Test
    fun letterboxPadsAndMapsBack() {
        val src = image(200, 100) { x, _ -> if (x < 100) 0xFF0000 else 0x0000FF }
        val lb = src.letterbox(64, padValue = 114)
        assertEquals(64, lb.image.width)
        assertEquals(0.32, lb.scale, 1e-9)
        assertEquals(0, lb.padX)
        assertEquals(16, lb.padY)
        assertEquals(114, lb.image.r(5, 2))
        assertEquals(255, lb.image.r(5, 32))
        assertEquals(100.0, lb.toSourceX(32.0), 1e-9)
        assertEquals(50.0, lb.toSourceY(32.0), 1e-9)
    }

    @Test
    fun tensorLayoutAndChannelOrder() {
        val img = image(2, 1) { x, _ -> if (x == 0) 0x0A141E else 0x28323C }
        val rgb = img.toTensor(ChannelOrder.RGB)
        assertEquals(listOf(1, 3, 1, 2), rgb.shape.toList())
        assertEquals(listOf(10f, 40f, 20f, 50f, 30f, 60f), rgb.data.toList())
        val bgr = img.toTensor(ChannelOrder.BGR, mean = 10f, scale = 0.5f)
        assertEquals(listOf(10f, 25f, 5f, 20f, 0f, 15f), bgr.data.toList())
    }

    @Test
    fun warpPerspectiveRecoversAxisAlignedRegion() {
        val src = image(100, 60) { x, y -> if (x in 20 until 70 && y in 10 until 40) 0x00FF00 else 0x101010 }
        val quad = Quad(Point(20.0, 10.0), Point(70.0, 10.0), Point(70.0, 40.0), Point(20.0, 40.0))
        val out = src.warpPerspective(quad, 50, 30)
        assertEquals(255, out.g(0, 0))
        assertEquals(255, out.g(49, 29))
        assertEquals(255, out.g(25, 15))
    }

    @Test
    fun qualityMetrics() {
        val flat = image(64, 64) { _, _ -> gray(200) }
        val checker = image(64, 64) { x, y -> if ((x / 2 + y / 2) % 2 == 0) gray(0) else gray(255) }
        assertTrue(checker.blurScore() > 1000.0)
        assertEquals(0.0, flat.blurScore(), 1e-9)
        assertEquals(200.0, flat.toGray().meanBrightness(), 1e-9)
        assertEquals(0.0, flat.saturatedRatio(), 1e-9)
        assertEquals(0.5, checker.saturatedRatio(), 0.05)
    }
}
