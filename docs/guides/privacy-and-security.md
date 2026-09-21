# Privacy & security

Faktel processes **biometric data** (faces) and **government ID images** (KTP). In Indonesia these fall under
personal-data protection rules (UU PDP No. 27/2022) and, for financial services, sector rules. This page is
engineering guidance, not legal advice - involve your legal/compliance team.

## What Faktel does

- Runs entirely on-device. It makes **no network calls** and has no telemetry.
- Holds images only in memory while you call it. It writes nothing to disk. (The iOS backend stages the *model* in a
  temp file briefly; it never writes images.)

## What you should do

1. **Do not persist raw KTP or selfie images** unless a documented, lawful purpose requires it. If you must, encrypt at
   rest and set retention limits.
2. **Treat embeddings as biometric templates.** Do not store or log them casually; store only if needed, encrypted.
3. **Do not log images, landmarks, embeddings or `KtpScanResult.card`.** Log scalar metrics (blur, glare, scores) only.
4. **Get explicit, informed consent** before capturing a face or an ID.
5. **Minimise transmission.** If a server step is required, send the least data (e.g. an embedding or a cropped
   portrait) over TLS, not full frames.
6. **Test data**: never commit real KTP images to a repository. Use synthetic KTPs or consented samples kept private.
7. **Clear buffers** promptly; do not keep `RgbImage` references longer than needed.

## Security limits you must design around

- **Passive liveness is not certified anti-spoofing.** It resists casual print and replay attacks only. Determined
  attackers (high-quality replays, masks, injection of a virtual camera) can defeat it. For high-assurance flows add
  active challenges, server-side risk checks, device attestation, and human review paths.
- **On-device checks can be bypassed** by an attacker controlling the device (hooking, virtual camera, patched app).
  Anything that gates value must be re-verified server-side.
- **KTP validation is shape/quality only.** It does not confirm the card is genuine or that the data is valid. Pair with
  authoritative verification (e.g. Dukcapil-based services) where required.
- **Face match accuracy is limited** by the embedding model and by the low quality of KTP portraits. Set thresholds
  by measured FAR/FRR, and provide a manual-review fallback.

## Reporting a vulnerability

See [SECURITY.md](../../SECURITY.md).
