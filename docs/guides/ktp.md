# KTP guide

All classes live in `io.github.cybersafetyid.faktel.ktp`.

## What is checked

The e-KTP is an ISO/IEC 7810 ID-1 card, **85.60 x 53.98 mm** (aspect ratio ~1.586), with the portrait on the right.
`KtpScanner` uses that as a strong prior:

1. **Locate** a card-shaped quadrilateral (`KtpDetector`).
2. **Rectify** it to an upright 1011x638 image (300 DPI equivalent) with a perspective transform.
3. **Validate**: aspect ratio, size in pixels, focus, glare, and (optionally) that a face sits where a KTP portrait sits.

It does **not** read text, verify authenticity, or detect forgeries.

## Usage

```kotlin
val scanner = KtpScanner(
    detector = ClassicalKtpDetector(),                       // default
    faceDetector = YuNetFaceDetector(engine, yunetBytes),   // optional, enables portrait checks
    config = KtpScanConfig(),
)
val scan: KtpScanResult = scanner.scan(image)
```

| `KtpScanResult` field | Meaning |
|---|---|
| `detection` | Where the card was found in the source image (`Quad` + confidence) |
| `card` | Rectified, upright card (1011x638) |
| `portrait` | Portrait face in **card** coordinates (needs a face detector) |
| `blurScore`, `glareRatio` | The measured values, useful for logging/tuning |
| `issues` | Empty means acceptable |

`KtpIssue` values and typical UI hints:

| Issue | Means | Hint to user |
|---|---|---|
| `CARD_NOT_FOUND` | No card-like object | "Place the KTP on a plain surface" |
| `BAD_ASPECT_RATIO` | Shape is not ~1.586 | "Show the whole card, flat" |
| `TOO_SMALL` | Too few pixels | "Move closer" |
| `BLURRY` | Out of focus / motion | "Hold steady" |
| `GLARE` | Laminate reflection | "Tilt to avoid reflections" |
| `PORTRAIT_NOT_FOUND` | No face on the card | "Show the front of the KTP" |
| `PORTRAIT_MISPLACED` | Face not on the right | wrong document or unusual layout |

A card that is upside-down (180 degrees) is recovered automatically when a face detector is supplied.
Sideways cards (90 degrees) are handled by the aspect-ratio normalisation.

## The classical detector and its limits

`ClassicalKtpDetector` is pure Kotlin: it estimates the background colour from the image border, segments what
differs (Otsu), takes the largest blob, and fits four corners from its convex hull. Requirements:

- a **plain, reasonably contrasting surface**,
- **visible margin** on all sides (the card must not fill the whole frame),
- card colour not identical to the surface.

It fails on cluttered backgrounds and full-frame cards. For those, implement `KtpDetector` with a learned model
(YOLO/segmentation) - that is the [planned](../roadmap.md) primary detector. Guide your users with an on-screen frame
and the issue hints above.

## Custom detector

```kotlin
class MyDetector : KtpDetector {
    override fun detect(image: RgbImage): KtpDetection? = /* run your model, return 4 corners */ null
}
val scanner = KtpScanner(detector = MyDetector(), faceDetector = face)
```

`Quad.fromUnordered(points)` sorts four points into the expected clockwise order.

## Camera flow suggestion

Analyse ~5 frames/second, show the first `issue` as a hint, and capture automatically once `isAcceptable` holds for
2-3 consecutive frames. Then use `scan.card` (not the raw frame) for whatever comes next.
