package io.github.cybersafetyid.faktel.ort

import io.github.cybersafetyid.faktel.core.image.ImageDecoder
import io.github.cybersafetyid.faktel.core.inference.readModelFile
import io.github.cybersafetyid.faktel.face.YuNetFaceDetector
import io.github.cybersafetyid.faktel.ktp.KtpScanConfig
import io.github.cybersafetyid.faktel.ktp.KtpScanner
import io.github.cybersafetyid.faktel.ktp.KtpSpec
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Skipped unless `FAKTEL_TEST_KTP_IMAGE` points at a photo of a (synthetic or consented) card. */
class RealKtpIntegrationTest {
    @Test
    fun scansCardPhotoEndToEnd() {
        val path = System.getenv("FAKTEL_TEST_KTP_IMAGE") ?: return
        val image = ImageDecoder.decode(File(path).readBytes())
        val yunet = YuNetFaceDetector(OrtInferenceEngine(), readModelFile("../models/face_detection_yunet_2023mar.onnx"))
        yunet.use {
            val r = KtpScanner(faceDetector = it, config = KtpScanConfig(minBlurScore = 0.0)).scan(image)
            println("FAKTEL ktp detection=${r.detection} issues=${r.issues} blur=${r.blurScore} glare=${r.glareRatio}")
            println("FAKTEL ktp portrait=${r.portrait?.box}")
            val card = assertNotNull(r.card)
            File(System.getProperty("java.io.tmpdir"), "faktel_card.rgb").writeBytes(card.data)
            assertTrue(card.width == KtpSpec.RECTIFIED_WIDTH)
            assertTrue(r.issues.isEmpty(), "unexpected issues ${r.issues}")
        }
    }
}
