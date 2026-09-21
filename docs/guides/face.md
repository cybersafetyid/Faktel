# Face guide

All classes live in `io.github.cybersafetyid.faktel.face`.

## Detect: `YuNetFaceDetector`

```kotlin
val detector = YuNetFaceDetector(engine, yunetBytes, YuNetFaceDetector.Config(scoreThreshold = 0.6))
val faces: List<Face> = detector.detect(image)      // sorted by score, best first
```

`Face` = `box: Rect` + `landmarks: FaceLandmarks` (5 points) + `score`. Coordinates are in `image` pixels.
Non-square images are letterboxed internally and mapped back; you do nothing.

| Config | Default | Meaning |
|---|---|---|
| `scoreThreshold` | 0.6 | Minimum confidence. Lower finds more (and false) faces. |
| `nmsThreshold` | 0.3 | IoU above which overlapping boxes merge. |
| `maxFaces` | 10 | Cap on results. |

Small faces: the model works on 640x640, so a face that is a tiny fraction of a large photo may be missed;
crop or use `minFaceWidthRatio` in the quality gate to demand a reasonably large face anyway.

## Head pose: `Face.headPose()`

Returns `HeadPose(yaw, pitch, roll)` in degrees derived geometrically from the landmarks. Roll is accurate; yaw and
pitch are **coarse** (good enough to reject profile/looking-up frames, not to measure angles).

## Quality gate: `FaceQualityAssessor`

```kotlin
val report = FaceQualityAssessor(FaceQualityConfig(minBlurScore = 30.0)).assess(image, face)
report.issues     // subset of TOO_SMALL, BLURRY, TOO_DARK, TOO_BRIGHT, FACE_ANGLE, FACE_CUT_OFF
```

No model involved; it is cheap. Defaults are conservative starting points - see [tuning](tuning.md).

## Liveness: `MiniFasNetLivenessDetector`

```kotlin
val live = MiniFasNetLivenessDetector(engine, fasBytes, cropScale = 2.7, threshold = 0.5)
val r = live.assess(image, face)      // LivenessResult(realScore, isLive)
```

- Use `cropScale = 2.7` for `MiniFASNetV2` (bundled) and `4.0` for `MiniFASNetV1SE`.
- Run it **only on frames that pass the quality gate** (`FaceAnalyzer` does this).
- **It is passive and single-frame.** It stops casual print/replay attacks. It is not certified anti-spoofing; add an
  active challenge or server-side checks for high-assurance flows. See [privacy & security](privacy-and-security.md).

## Pipeline: `FaceAnalyzer`

```kotlin
val analyzer = FaceAnalyzer(detector, FaceQualityAssessor(), liveness)   // liveness optional
val result = analyzer.analyze(frame)
```

`FaceAnalysis` has `faces`, the largest `face`, its `quality`, `liveness` (null if skipped), and `issues`.
`isAcceptable` is true only if there are no issues **and** liveness (if configured) says live. `MULTIPLE_FACES`
is reported when more than one face is found; the largest one is analysed. `close()` closes the detector and liveness
model - the analyzer owns them.

## Embedding & matching

See [selfie-to-KTP matching](selfie-ktp-matching.md).

## Extending

Implement `FaceDetector`, `LivenessDetector` or `FaceEmbedder` to plug in another model. The interfaces are
deliberately tiny; see [CONTRIBUTING](../../CONTRIBUTING.md#adding-a-model-or-backend).
