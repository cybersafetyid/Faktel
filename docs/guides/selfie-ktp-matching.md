# Selfie-to-KTP matching

Goal: is the person in the selfie the same as the portrait on the KTP?

```kotlin
val faceDetector = YuNetFaceDetector(engine, yunet)
val embedder = ArcFaceEmbedder(engine, arcFaceBytes)     // your model, see models/MODELS.md
val matcher = FaceMatcher(threshold = 0.30)              // calibrate!

// 1. KTP side
val scan = KtpScanner(faceDetector = faceDetector).scan(ktpPhoto)
require(scan.isAcceptable) { scan.issues }
val ktpEmbedding = embedder.embed(scan.card!!, scan.portrait!!)

// 2. Selfie side
val selfie = FaceAnalyzer(faceDetector, liveness = liveness).analyze(selfieFrame)
require(selfie.isAcceptable)
val selfieEmbedding = embedder.embed(selfieFrame, selfie.face!!)

// 3. Compare
val result = matcher.compare(selfieEmbedding, ktpEmbedding)   // FaceMatchResult(similarity, isMatch)
```

Under the hood `ArcFaceEmbedder` aligns each face to the standard 112x112 template using the five landmarks, so both
inputs get identical geometry, and returns an L2-normalised vector. `FaceMatcher` is a cosine-similarity threshold.

## Things that matter

- **The threshold is not universal.** A KTP portrait is small, laminated and often old, so genuine selfie-vs-KTP
  similarity is lower than selfie-vs-selfie. Measure genuine and impostor pairs from *your* population and choose the
  threshold for your target false-accept rate ([tuning](tuning.md)).
- **Do both the liveness and quality checks on the selfie first.** Matching a spoof proves nothing.
- **Consider a fallback.** If accuracy from a mobile embedder is not enough, send the *embedding* (never the raw
  images) to a server tier with a larger model and decide there.
- **Do not store embeddings casually** - they are biometric templates. See [privacy](privacy-and-security.md).
- **Model licence.** Faktel bundles no embedding model; check the licence of the weights you use
  ([models](../../models/MODELS.md)).
