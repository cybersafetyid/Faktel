# Tuning thresholds

Every default in Faktel is a **conservative starting point**, not a measured optimum. Cameras, lighting, and user
populations differ enormously. Tune before you ship.

## What to tune

| Setting | Where | Effect of raising it |
|---|---|---|
| `YuNetFaceDetector.Config.scoreThreshold` (0.6) | detector | fewer false faces, more missed faces |
| `FaceQualityConfig.minBlurScore` (30) | face quality | rejects more soft images |
| `FaceQualityConfig.min/maxBrightness` (60/200) | face quality | narrows the accepted exposure range |
| `FaceQualityConfig.minFaceWidthRatio` (0.2) | face quality | demands larger faces |
| `FaceQualityConfig.maxYaw/Pitch/Roll` (25) | face quality | lower = stricter frontal requirement |
| `MiniFasNetLivenessDetector.threshold` (0.5) | liveness | fewer spoofs accepted, more genuine users rejected |
| `FaceMatcher.threshold` (0.30) | matching | fewer impostors accepted, more genuine users rejected |
| `KtpScanConfig.aspectTolerance` (0.15) | KTP | lower = stricter shape |
| `KtpScanConfig.minCardWidthPx` (500) | KTP | demands more pixels on the card |
| `KtpScanConfig.minBlurScore` (40) | KTP | rejects softer cards |
| `KtpScanConfig.maxGlareRatio` (0.03) | KTP | lower = tolerates less reflection |

## Method

1. **Log the measurements**, not just pass/fail: `FaceQualityReport.blurScore/brightness/pose`,
   `KtpScanResult.blurScore/glareRatio`, `LivenessResult.realScore`, `FaceMatchResult.similarity`.
2. **Collect labelled samples** on your target devices (consented; use synthetic or your own cards for KTP).
   For each check you want both "good" and "should be rejected" examples.
3. **Plot the two distributions** and choose the threshold at your desired trade-off.
4. For matching and liveness, compute the **false-accept rate (FAR)** and **false-reject rate (FRR)** across
   thresholds and pick the point matching your risk appetite (e.g. FAR <= 0.1%). The right point is a business decision.
5. **Re-measure per device tier.** Blur scores in particular shift with sensor resolution and sharpening.
6. Version the chosen values in your app config so you can change them without a release.

## Notes on blur scores

Blur score is the variance of the Laplacian on a resolution-normalised crop (face: 128 px, card: 512 px). It depends
on content: a plain, well-lit face scores lower than a textured one. Treat it as *relative* on your data, not as a
universal number.

## Performance tuning

- Detect every frame, but run liveness/embedding only on frames that pass the quality gate.
- Lower analysis resolution first if fps is too low; then increase `numThreads`.
- Reuse sessions; never create one per frame.
- Benchmark on real mid-range devices. Published numbers for these exact models under ONNX Runtime are scarce.
