package io.github.cybersafetyid.faktel.face

import io.github.cybersafetyid.faktel.core.image.RgbImage

/**
 * Result of [FaceAnalyzer.analyze].
 *
 * @property face the largest detected face, or null when none was found
 * @property quality quality report for [face]
 * @property liveness liveness verdict, or null if no [LivenessDetector] is configured, no face was found, or the
 * quality gate failed (liveness is skipped on unusable frames to save compute)
 * @property issues every problem found; empty and [isAcceptable] when the frame is good
 */
public data class FaceAnalysis(
    val faces: List<Face>,
    val face: Face?,
    val quality: FaceQualityReport?,
    val liveness: LivenessResult?,
    val issues: List<FaceIssue>,
) {
    public val isAcceptable: Boolean get() = issues.isEmpty() && (liveness?.isLive ?: true)
}

/**
 * Selfie pipeline: detect -> quality gate -> optional liveness. Cheap detector runs on every frame; the heavier
 * liveness model only runs on frames that already pass quality checks.
 */
public class FaceAnalyzer(
    private val detector: FaceDetector,
    private val quality: FaceQualityAssessor = FaceQualityAssessor(),
    private val liveness: LivenessDetector? = null,
) : AutoCloseable {

    public fun analyze(image: RgbImage): FaceAnalysis {
        val faces = detector.detect(image)
        val primary = faces.maxByOrNull { it.box.area }
            ?: return FaceAnalysis(faces, null, null, null, listOf(FaceIssue.NO_FACE))
        val report = quality.assess(image, primary)
        val issues = buildList {
            if (faces.size > 1) add(FaceIssue.MULTIPLE_FACES)
            addAll(report.issues)
        }
        val live = if (report.isAcceptable) liveness?.assess(image, primary) else null
        return FaceAnalysis(faces, primary, report, live, issues)
    }

    /** Closes the detector and liveness models (the analyzer owns them). */
    override fun close() {
        detector.close()
        liveness?.close()
    }
}
