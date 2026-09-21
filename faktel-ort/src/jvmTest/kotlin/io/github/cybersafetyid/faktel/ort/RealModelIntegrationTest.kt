package io.github.cybersafetyid.faktel.ort

import io.github.cybersafetyid.faktel.core.image.ImageDecoder
import io.github.cybersafetyid.faktel.core.inference.readModelFile
import io.github.cybersafetyid.faktel.face.ArcFaceEmbedder
import io.github.cybersafetyid.faktel.face.FaceAnalyzer
import io.github.cybersafetyid.faktel.face.FaceMatcher
import io.github.cybersafetyid.faktel.face.MiniFasNetLivenessDetector
import io.github.cybersafetyid.faktel.face.YuNetFaceDetector
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runs the real bundled models through real ONNX Runtime on the desktop JVM.
 *
 * Skipped unless `FAKTEL_TEST_IMAGE` points at a photo containing a face (test photos are deliberately not
 * committed to the repository). Optionally set `FAKTEL_EMBEDDER_MODEL` to an ArcFace ONNX file.
 */
class RealModelIntegrationTest {
    private val imagePath: String? = System.getenv("FAKTEL_TEST_IMAGE")
    private val models = File("../models")

    @Test
    fun detectsFaceAndScoresLiveness() {
        val path = imagePath ?: return
        val image = ImageDecoder.decode(File(path).readBytes())
        val engine = OrtInferenceEngine()
        val detector = YuNetFaceDetector(engine, readModelFile("$models/face_detection_yunet_2023mar.onnx"))
        val liveness = MiniFasNetLivenessDetector(engine, readModelFile("$models/minifasnet_v2.onnx"))
        FaceAnalyzer(detector, liveness = liveness).use { analyzer ->
            val result = analyzer.analyze(image)
            val face = assertNotNull(result.face, "no face found in $path")
            println("FAKTEL faces=${result.faces.size} box=${face.box} score=${face.score}")
            println("FAKTEL landmarks=${face.landmarks}")
            println("FAKTEL quality=${result.quality} issues=${result.issues}")
            println("FAKTEL liveness=${result.liveness}")
            File(System.getProperty("java.io.tmpdir"), "faktel_face.txt").writeText(
                "${face.box.left} ${face.box.top} ${face.box.right} ${face.box.bottom}\n" +
                    face.landmarks.asList().joinToString("\n") { "${it.x} ${it.y}" },
            )
            assertTrue(face.score > 0.6)
            val l = face.landmarks
            assertTrue(l.rightEye.x < l.leftEye.x, "right eye must be left of left eye in a frontal photo")
            assertTrue(l.nose.y > l.rightEye.y && l.rightMouth.y > l.nose.y)
        }
    }

    @Test
    fun sameFaceEmbedsToHighSimilarity() {
        val path = imagePath ?: return
        val embedderPath = System.getenv("FAKTEL_EMBEDDER_MODEL") ?: return
        val image = ImageDecoder.decode(File(path).readBytes())
        val engine = OrtInferenceEngine()
        YuNetFaceDetector(engine, readModelFile("$models/face_detection_yunet_2023mar.onnx")).use { det ->
            ArcFaceEmbedder(engine, readModelFile(embedderPath)).use { emb ->
                val face = det.detect(image).first()
                val a = emb.embed(image, face)
                val b = emb.embed(image.flipHorizontal(), det.detect(image.flipHorizontal()).first())
                val r = FaceMatcher().compare(a, b)
                println("FAKTEL selfMatch=$r dim=${a.values.size}")
                assertTrue(a.values.size == 512 && r.similarity > 0.5)
            }
        }
    }
}
