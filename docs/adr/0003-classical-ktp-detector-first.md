# ADR 0003: Ship a classical KTP card detector first

- Status: accepted
- Date: 2026-09-21

## Context

The research recommends a learned card detector (small YOLO/segmentation model) as the primary approach, with classical
computer vision as a fallback. A learned detector needs training data. Real KTP images are regulated personal data, so
the dataset must be synthetic or consented, and building/validating that dataset is a project of its own.

## Decision

Ship, in the first release, a pure-Kotlin classical detector (background segmentation -> convex hull -> four-corner
fit), the full downstream pipeline (rectification, aspect-ratio gate, size/blur/glare/portrait validation), and a
`KtpDetector` interface. Defer the learned detector to the [roadmap](../roadmap.md).

## Consequences

- The whole KTP pipeline is usable and tested today with no model and no native code.
- The classical detector has real limits (plain contrasting background, margin around the card); they are documented and
  surfaced as `CARD_NOT_FOUND` rather than hidden.
- Adding a learned detector is a new `KtpDetector` implementation; nothing downstream changes.
- We do not claim robustness on cluttered scenes until the learned detector ships and is benchmarked.
