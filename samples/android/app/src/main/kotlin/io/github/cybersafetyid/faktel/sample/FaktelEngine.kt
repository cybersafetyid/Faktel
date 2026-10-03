package io.github.cybersafetyid.faktel.sample

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import io.github.cybersafetyid.faktel.core.image.RgbImage
import io.github.cybersafetyid.faktel.core.image.toRgbImage
import io.github.cybersafetyid.faktel.core.inference.SessionOptions
import io.github.cybersafetyid.faktel.core.inference.readModelAsset
import io.github.cybersafetyid.faktel.face.FaceAnalysis
import io.github.cybersafetyid.faktel.face.FaceAnalyzer
import io.github.cybersafetyid.faktel.face.MiniFasNetLivenessDetector
import io.github.cybersafetyid.faktel.face.YuNetFaceDetector
import io.github.cybersafetyid.faktel.ktp.KtpScanResult
import io.github.cybersafetyid.faktel.ktp.KtpScanner
import io.github.cybersafetyid.faktel.ort.OrtInferenceEngine
import java.io.ByteArrayInputStream
import kotlin.system.measureTimeMillis

/** The whole Faktel integration: one ONNX engine, two models, two pipelines. Not thread-safe: call from one thread. */
class FaktelEngine(context: Context) {
    private val ort = OrtInferenceEngine()
    private val yunet = context.readModelAsset("face_detection_yunet_2023mar.onnx")
    private val fas = context.readModelAsset("minifasnet_v2.onnx")
    private val opts = SessionOptions(numThreads = 2)

    private val selfie = FaceAnalyzer(
        detector = YuNetFaceDetector(ort, yunet, options = opts),
        liveness = MiniFasNetLivenessDetector(ort, fas, options = opts),
    )
    private val ktp = KtpScanner(faceDetector = YuNetFaceDetector(ort, yunet, options = opts))

    fun analyzeSelfie(image: RgbImage): Timed<FaceAnalysis> = timed { selfie.analyze(image) }
    fun scanKtp(image: RgbImage): Timed<KtpScanResult> = timed { ktp.scan(image) }
}

class Timed<T>(val value: T, val millis: Long)

private inline fun <T> timed(block: () -> T): Timed<T> {
    var v: T? = null
    val ms = measureTimeMillis { v = block() }
    @Suppress("UNCHECKED_CAST")
    return Timed(v as T, ms)
}

/** Decodes photo bytes and applies EXIF rotation (Faktel's decoder deliberately does not). */
fun decodeUpright(bytes: ByteArray, maxSide: Int = 1800): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    val m = Matrix()
    when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
        else -> return bmp
    }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
}

fun RgbImage.toBitmap(): Bitmap {
    val px = IntArray(width * height) { i ->
        (0xFF shl 24) or ((data[i * 3].toInt() and 0xFF) shl 16) or
            ((data[i * 3 + 1].toInt() and 0xFF) shl 8) or (data[i * 3 + 2].toInt() and 0xFF)
    }
    return Bitmap.createBitmap(px, width, height, Bitmap.Config.ARGB_8888)
}

fun Bitmap.asRgb(): RgbImage = toRgbImage()
