package io.github.cybersafetyid.faktel.face

import io.github.cybersafetyid.faktel.core.FaktelException
import io.github.cybersafetyid.faktel.core.geometry.Point
import io.github.cybersafetyid.faktel.core.geometry.Rect
import io.github.cybersafetyid.faktel.core.inference.Tensor
import kotlin.math.abs
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FaceTest {
    private val outNames = listOf(8, 16, 32).flatMap { s -> listOf("cls_$s", "obj_$s", "bbox_$s", "kps_$s") }

    /** YuNet outputs with a single confident detection at grid cell (row, col) of [stride]. */
    private fun yunetOutputs(stride: Int, row: Int, col: Int, w: Float, h: Float, conf: Float = 0.9f): Map<String, Tensor> {
        val out = HashMap<String, Tensor>()
        for (s in listOf(8, 16, 32)) {
            val n = 640 / s
            val cls = FloatArray(n * n)
            val obj = FloatArray(n * n)
            val bbox = FloatArray(n * n * 4)
            val kps = FloatArray(n * n * 10)
            if (s == stride) {
                val i = row * n + col
                cls[i] = conf
                obj[i] = conf
                bbox[4 * i] = 0.5f; bbox[4 * i + 1] = 0.5f
                bbox[4 * i + 2] = ln(w / s); bbox[4 * i + 3] = ln(h / s)
                for (k in 0 until 5) { kps[10 * i + 2 * k] = 0.5f + k * 0.1f; kps[10 * i + 2 * k + 1] = 0.25f * k }
            }
            out["cls_$s"] = Tensor(intArrayOf(1, n * n, 1), cls)
            out["obj_$s"] = Tensor(intArrayOf(1, n * n, 1), obj)
            out["bbox_$s"] = Tensor(intArrayOf(1, n * n, 4), bbox)
            out["kps_$s"] = Tensor(intArrayOf(1, n * n, 10), kps)
        }
        return out
    }

    private fun detector(engine: FakeEngine, config: YuNetFaceDetector.Config = YuNetFaceDetector.Config()) =
        YuNetFaceDetector(engine, ByteArray(0), config)

    @Test
    fun yunetDecodesAndMapsBackThroughLetterbox() {
        // 1280x640 source -> scale 0.5, padY = 160. Face at model coords centre (320, 320) size 64x80.
        val engine = FakeEngine(listOf("input"), outNames) { yunetOutputs(16, row = 19, col = 19, w = 64f, h = 80f) }
        val faces = detector(engine).detect(solid(1280, 640))
        assertEquals(1, faces.size)
        val f = faces.single()
        // cell (19,19)+0.5 -> centre (312, 312)
        assertEquals((312.0 - 32) / 0.5, f.box.left, 1e-3)
        assertEquals((312.0 - 40 - 160) / 0.5, f.box.top, 1e-3)
        assertEquals(64.0 / 0.5, f.box.width, 1e-3)
        assertEquals(80.0 / 0.5, f.box.height, 1e-3)
        assertEquals(0.9, f.score, 1e-6)
        // first landmark: (0.5 + col) * stride = 19.5*16 = 312 -> x/scale
        assertEquals(312.0 / 0.5, f.landmarks.rightEye.x, 1e-3)
        val input = engine.fed.single().getValue("input")
        assertContentEquals(intArrayOf(1, 3, 640, 640), input.shape)
    }

    @Test
    fun yunetSuppressesLowScoresAndDuplicates() {
        val low = FakeEngine(listOf("input"), outNames) { yunetOutputs(8, 10, 10, 40f, 40f, conf = 0.3f) }
        assertEquals(0, detector(low).detect(solid(640, 640)).size)

        // Two overlapping candidates from different strides collapse into one via NMS.
        val a = yunetOutputs(16, 20, 20, 64f, 64f)
        val b = yunetOutputs(8, 40, 40, 64f, 64f, conf = 0.8f)
        val merged = HashMap<String, Tensor>()
        for (k in a.keys) merged[k] = if (k.endsWith("_8")) b.getValue(k) else a.getValue(k)
        val engine = FakeEngine(listOf("input"), outNames) { merged }
        assertEquals(1, detector(engine).detect(solid(640, 640)).size)
    }

    @Test
    fun yunetRejectsForeignModel() {
        val engine = FakeEngine(listOf("input"), listOf("logits")) { emptyMap() }
        assertFailsWith<FaktelException.ModelLoad> { detector(engine) }
        assertTrue(engine.closed, "session must be released when validation fails")
    }

    @Test
    fun frontalPoseIsNearZeroAndTurnedHeadHasYaw() {
        val frontal = Face(Rect(0.0, 0.0, 100.0, 100.0),
            FaceLandmarks(Point(30.0, 40.0), Point(70.0, 40.0), Point(50.0, 61.0), Point(35.0, 78.0), Point(65.0, 78.0)), 0.9)
        val p = frontal.headPose()
        assertTrue(abs(p.yaw) < 3 && abs(p.roll) < 1 && abs(p.pitch) < 8, "$p")
        val turned = frontal.copy(landmarks = frontal.landmarks.copy(nose = Point(62.0, 61.0)))
        assertTrue(turned.headPose().yaw > 20, "${turned.headPose()}")
        val tilted = frontal.copy(landmarks = frontal.landmarks.copy(leftEye = Point(70.0, 52.0)))
        assertTrue(tilted.headPose().roll > 15)
    }

    @Test
    fun qualityFlagsSmallDarkBlurryAndCutOffFaces() {
        val cfg = FaceQualityConfig(minBlurScore = 0.0)
        val assessor = FaceQualityAssessor(cfg)
        val img = solid(400, 400, v = 20)
        val face = fakeFace(200.0, 200.0, half = 20.0)
        val issues = assessor.assess(img, face).issues
        assertTrue(FaceIssue.TOO_SMALL in issues && FaceIssue.TOO_DARK in issues, "$issues")
        val bright = FaceQualityAssessor(cfg).assess(solid(400, 400, v = 250), fakeFace(200.0, 200.0, half = 100.0))
        assertEquals(listOf(FaceIssue.TOO_BRIGHT), bright.issues)
        val blurry = FaceQualityAssessor().assess(solid(400, 400, v = 128), fakeFace(200.0, 200.0, half = 100.0))
        assertEquals(listOf(FaceIssue.BLURRY), blurry.issues)
        val cut = assessor.assess(solid(400, 400), fakeFace(5.0, 200.0, half = 100.0))
        assertTrue(FaceIssue.FACE_CUT_OFF in cut.issues)
    }

    @Test
    fun livenessCropMatchesReferenceImplementation() {
        // Reference (Python): image 640x480, bbox xywh=(300,200,100,100), scale=min(479/100, 639/100, 2.7)=2.7
        // new=270, centre=(350,250) -> x1=215 y1=115 x2=485 y2=385, inclusive -> 271x271 crop.
        val crop = MiniFasNetLivenessDetector.cropForLiveness(solid(640, 480), Rect(300.0, 200.0, 400.0, 300.0), 2.7)
        assertEquals(271, crop.width)
        assertEquals(271, crop.height)
        // Near the border the scale is limited by the image: bbox spans almost the full height.
        val big = MiniFasNetLivenessDetector.cropForLiveness(solid(640, 480), Rect(100.0, 10.0, 400.0, 470.0), 2.7)
        assertTrue(big.height <= 480 && big.width <= 640)
    }

    @Test
    fun livenessUsesClassOneAsRealAndThreshold() {
        fun run(logits: FloatArray, thr: Double = 0.5): LivenessResult {
            val engine = FakeEngine(listOf("input"), listOf("output")) { mapOf("output" to Tensor(intArrayOf(1, 3), logits)) }
            val det = MiniFasNetLivenessDetector(engine, ByteArray(0), threshold = thr)
            val r = det.assess(solid(300, 300), fakeFace(150.0, 150.0))
            val t = engine.fed.single().getValue("input")
            assertContentEquals(intArrayOf(1, 3, 80, 80), t.shape)
            return r
        }
        val real = run(floatArrayOf(-2f, 3f, -2f))
        assertTrue(real.isLive && real.realScore > 0.97)
        val spoof = run(floatArrayOf(4f, -1f, 0f))
        assertTrue(!spoof.isLive && spoof.realScore < 0.05)
        assertTrue(!run(floatArrayOf(-2f, 3f, -2f), thr = 0.999).isLive)
        val bad = FakeEngine(listOf("input"), listOf("o")) { mapOf("o" to Tensor(intArrayOf(1, 2), floatArrayOf(0f, 1f))) }
        assertFailsWith<FaktelException.Inference> {
            MiniFasNetLivenessDetector(bad, ByteArray(0)).assess(solid(300, 300), fakeFace(150.0, 150.0))
        }
    }

    @Test
    fun embeddingIsNormalisedAndCosineWorks() {
        val a = FaceEmbedding(floatArrayOf(3f, 4f))
        assertEquals(0.6f, a.values[0], 1e-6f)
        assertEquals(1.0, a.cosineSimilarity(a), 1e-6)
        assertEquals(0.0, a.cosineSimilarity(FaceEmbedding(floatArrayOf(-4f, 3f))), 1e-6)
        assertEquals(-1.0, a.cosineSimilarity(FaceEmbedding(floatArrayOf(-3f, -4f))), 1e-6)
        assertFailsWith<IllegalArgumentException> { FaceEmbedding(floatArrayOf(0f, 0f)) }
        assertFailsWith<IllegalArgumentException> { a.cosineSimilarity(FaceEmbedding(floatArrayOf(1f, 2f, 3f))) }
        val m = FaceMatcher(threshold = 0.5)
        assertTrue(m.compare(a, a).isMatch)
        assertTrue(!m.compare(a, FaceEmbedding(floatArrayOf(-4f, 3f))).isMatch)
    }

    @Test
    fun embedderAlignsToTemplateAndNormalises() {
        // A face whose landmarks already equal the template must warp to (nearly) the identity.
        val t = ArcFaceEmbedder.TEMPLATE
        val lm = FaceLandmarks(t[0], t[1], t[2], t[3], t[4])
        val face = Face(Rect(0.0, 0.0, 112.0, 112.0), lm, 1.0)
        val src = io.github.cybersafetyid.faktel.core.image.RgbImage(112, 112,
            ByteArray(112 * 112 * 3) { ((it / 3) % 112 * 2).toByte() })
        val aligned = ArcFaceEmbedder.align(src, face)
        assertEquals(src.r(50, 50), aligned.r(50, 50))

        val engine = FakeEngine(listOf("input.1"), listOf("emb")) { mapOf("emb" to Tensor(intArrayOf(1, 4), floatArrayOf(1f, 2f, 2f, 4f))) }
        val e = ArcFaceEmbedder(engine, ByteArray(0)).embed(src, face)
        assertEquals(1.0, e.cosineSimilarity(e), 1e-6)
        val input = engine.fed.single().getValue("input.1")
        assertContentEquals(intArrayOf(1, 3, 112, 112), input.shape)
        assertTrue(input.data.all { it in -1.0001f..1.0001f })
    }

    @Test
    fun analyzerFlow() {
        val goodFace = fakeFace(200.0, 200.0, half = 100.0)
        val detector = object : FaceDetector {
            var result: List<Face> = emptyList()
            override fun detect(image: io.github.cybersafetyid.faktel.core.image.RgbImage) = result
            override fun close() {}
        }
        var livenessCalls = 0
        val liveness = object : LivenessDetector {
            override fun assess(image: io.github.cybersafetyid.faktel.core.image.RgbImage, face: Face): LivenessResult {
                livenessCalls++
                return LivenessResult(0.2, false)
            }
            override fun close() {}
        }
        val quality = FaceQualityAssessor(FaceQualityConfig(minBlurScore = 0.0))
        val analyzer = FaceAnalyzer(detector, quality, liveness)
        val img = solid(400, 400)

        assertEquals(listOf(FaceIssue.NO_FACE), analyzer.analyze(img).issues)
        assertEquals(0, livenessCalls)

        detector.result = listOf(goodFace)
        val ok = analyzer.analyze(img)
        assertNotNull(ok.face)
        assertEquals(1, livenessCalls)
        assertTrue(!ok.isAcceptable, "spoof verdict must make the analysis unacceptable")

        detector.result = listOf(goodFace, fakeFace(100.0, 100.0, half = 30.0))
        val multi = analyzer.analyze(img)
        assertTrue(FaceIssue.MULTIPLE_FACES in multi.issues)
        assertEquals(goodFace, multi.face)

        detector.result = listOf(fakeFace(200.0, 200.0, half = 10.0))
        val small = analyzer.analyze(img)
        assertNull(small.liveness, "liveness must be skipped when the quality gate fails")
        assertEquals(2, livenessCalls)
    }
}
