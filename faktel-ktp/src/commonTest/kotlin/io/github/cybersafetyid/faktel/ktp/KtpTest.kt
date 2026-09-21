package io.github.cybersafetyid.faktel.ktp

import io.github.cybersafetyid.faktel.core.geometry.Point
import io.github.cybersafetyid.faktel.core.geometry.Quad
import io.github.cybersafetyid.faktel.core.geometry.Rect
import io.github.cybersafetyid.faktel.core.image.RgbImage
import io.github.cybersafetyid.faktel.face.Face
import io.github.cybersafetyid.faktel.face.FaceDetector
import io.github.cybersafetyid.faktel.face.FaceLandmarks
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KtpTest {
    private fun assertCornersNear(expected: Quad, actual: Quad, tol: Double) {
        expected.corners.zip(actual.corners).forEachIndexed { i, (e, a) ->
            assertTrue(e.distanceTo(a) <= tol, "corner $i: expected $e got $a (tol $tol)")
        }
    }

    @Test
    fun findsAxisAlignedCard() {
        val q = cardQuad(400.0, 300.0, 476.0, 300.0)
        val det = assertNotNull(ClassicalKtpDetector().detect(scene(800, 600, q)))
        assertCornersNear(q, det.quad, 5.0)
        assertTrue(det.confidence > 0.95)
    }

    @Test
    fun findsCardRotated30And45Degrees() {
        for (deg in listOf(30.0, 45.0, -20.0)) {
            val q = cardQuad(420.0, 310.0, 476.0, 300.0, deg)
            val det = assertNotNull(ClassicalKtpDetector().detect(scene(840, 620, q)), "rotation $deg")
            // The starting corner of a strongly rotated card is inherently ambiguous, so compare as sets.
            for (e in q.corners) {
                assertTrue(det.quad.corners.any { it.distanceTo(e) <= 6.0 }, "rotation $deg: no corner near $e in ${det.quad}")
            }
        }
    }

    @Test
    fun findsPerspectiveSkewedCard() {
        val q = Quad(Point(180.0, 120.0), Point(640.0, 160.0), Point(690.0, 440.0), Point(150.0, 400.0))
        val det = assertNotNull(ClassicalKtpDetector().detect(scene(800, 600, q)))
        assertCornersNear(q, det.quad, 6.0)
    }

    @Test
    fun noCardInUniformOrTinyScene() {
        val flat = RgbImage(400, 300, ByteArray(400 * 300 * 3) { 120 })
        assertNull(ClassicalKtpDetector().detect(flat))
        val tiny = scene(800, 600, cardQuad(400.0, 300.0, 40.0, 25.0))
        assertNull(ClassicalKtpDetector().detect(tiny))
    }

    @Test
    fun scannerAcceptsRealisticCardWithoutFaceDetector() {
        val q = cardQuad(500.0, 350.0, 760.0, 479.0, 8.0)
        val r = KtpScanner(config = KtpScanConfig(minBlurScore = 0.0)).scan(scene(1000, 700, q))
        assertEquals(emptyList(), r.issues)
        val card = assertNotNull(r.card)
        assertEquals(KtpSpec.RECTIFIED_WIDTH, card.width)
        // Rectified card should be filled with the card colour (blue-dominant) even at its corners.
        assertTrue(card.b(10, 10) > card.r(10, 10) && card.b(1000, 630) > card.r(1000, 630))
    }

    @Test
    fun scannerFlagsWrongShapeAndNotFound() {
        val square = cardQuad(400.0, 300.0, 300.0, 300.0)
        val r = KtpScanner(config = KtpScanConfig(minBlurScore = 0.0, minCardWidthPx = 100)).scan(scene(800, 600, square))
        assertTrue(KtpIssue.BAD_ASPECT_RATIO in r.issues)
        val none = KtpScanner().scan(RgbImage(200, 200, ByteArray(200 * 200 * 3) { 90 }))
        assertEquals(listOf(KtpIssue.CARD_NOT_FOUND), none.issues)
        assertNull(none.card)
    }

    @Test
    fun scannerFlagsSmallAndBlurryCard() {
        val q = cardQuad(200.0, 150.0, 200.0, 126.0)
        val r = KtpScanner().scan(scene(400, 300, q))
        assertTrue(KtpIssue.TOO_SMALL in r.issues)
        assertTrue(KtpIssue.BLURRY in r.issues, "flat colour has no texture -> variance 0")
    }

    @Test
    fun portraitOnRightIsAcceptedAndUpsideDownIsRecovered() {
        val q = cardQuad(500.0, 350.0, 760.0, 479.0)
        val img = scene(1000, 700, q)
        // Fake detector: reports a face on the right half of whatever card it is given, only if the "upright"
        // marker (red pixel patch at the card's top-left) is at the top-left of the given image.
        val rightFace = face(0.8 * KtpSpec.RECTIFIED_WIDTH, 0.5 * KtpSpec.RECTIFIED_HEIGHT)
        var calls = 0
        val fd = object : FaceDetector {
            override fun detect(image: RgbImage): List<Face> {
                calls++
                return if (calls == 1) emptyList() else listOf(rightFace) // first (upright) view misses, flip hits
            }
            override fun close() {}
        }
        val r = KtpScanner(faceDetector = fd, config = KtpScanConfig(minBlurScore = 0.0)).scan(img)
        assertEquals(2, calls)
        assertEquals(rightFace, r.portrait)
        assertEquals(emptyList(), r.issues)
    }

    @Test
    fun faceOnLeftIsMisplacedAndMissingFaceIsNotFound() {
        val img = scene(1000, 700, cardQuad(500.0, 350.0, 760.0, 479.0))
        val cfg = KtpScanConfig(minBlurScore = 0.0)
        val left = face(0.2 * KtpSpec.RECTIFIED_WIDTH, 0.5 * KtpSpec.RECTIFIED_HEIGHT)
        val misplaced = KtpScanner(faceDetector = fixedFaces(listOf(left)), config = cfg).scan(img)
        assertEquals(listOf(KtpIssue.PORTRAIT_MISPLACED), misplaced.issues)
        val missing = KtpScanner(faceDetector = fixedFaces(emptyList()), config = cfg).scan(img)
        assertEquals(listOf(KtpIssue.PORTRAIT_NOT_FOUND), missing.issues)
    }

    private fun fixedFaces(faces: List<Face>) = object : FaceDetector {
        override fun detect(image: RgbImage) = faces
        override fun close() {}
    }

    private fun face(cx: Double, cy: Double): Face {
        val p = Point(cx, cy)
        return Face(Rect(cx - 50, cy - 60, cx + 50, cy + 60), FaceLandmarks(p, p, p, p, p), 0.9)
    }

    @Test
    fun aspectSpecMatchesId1() {
        assertTrue(abs(KtpSpec.ASPECT_RATIO - 1.5858) < 0.001)
        assertTrue(abs(KtpSpec.RECTIFIED_WIDTH.toDouble() / KtpSpec.RECTIFIED_HEIGHT - KtpSpec.ASPECT_RATIO) < 0.002)
    }
}
