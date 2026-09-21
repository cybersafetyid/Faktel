package io.github.cybersafetyid.faktel.core.image

/**
 * Decodes encoded image files (JPEG, PNG, ...) into an [RgbImage] using the platform's native codec.
 *
 * EXIF orientation is **not** applied: cameras often store sensor-oriented pixels plus an orientation tag.
 * Apply [RgbImage.rotate] yourself if your source carries such metadata.
 */
public expect object ImageDecoder {
    /** @throws io.github.cybersafetyid.faktel.core.FaktelException.InvalidImage if [bytes] cannot be decoded. */
    public fun decode(bytes: ByteArray): RgbImage
}
