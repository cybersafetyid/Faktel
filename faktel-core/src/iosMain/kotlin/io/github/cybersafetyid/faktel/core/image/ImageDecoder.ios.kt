@file:OptIn(ExperimentalForeignApi::class)

package io.github.cybersafetyid.faktel.core.image

import io.github.cybersafetyid.faktel.core.FaktelException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.ImageIO.CGImageSourceCreateImageAtIndex
import platform.ImageIO.CGImageSourceCreateWithData

public actual object ImageDecoder {
    public actual fun decode(bytes: ByteArray): RgbImage {
        if (bytes.isEmpty()) throw FaktelException.InvalidImage("Empty image data")
        val cfData = bytes.usePinned { CFDataCreate(null, it.addressOf(0).reinterpret(), bytes.size.convert()) }
            ?: throw FaktelException.InvalidImage("Could not wrap image bytes")
        val source = CGImageSourceCreateWithData(cfData, null)
        CFRelease(cfData)
        if (source == null) throw FaktelException.InvalidImage("Unsupported or corrupt image data")
        val image = CGImageSourceCreateImageAtIndex(source, 0u, null)
        CFRelease(source)
        if (image == null) throw FaktelException.InvalidImage("Unsupported or corrupt image data")

        val w = CGImageGetWidth(image).toInt()
        val h = CGImageGetHeight(image).toInt()
        val rgba = ByteArray(w * h * 4)
        val space = CGColorSpaceCreateDeviceRGB()
        try {
            rgba.usePinned { pinned ->
                val ctx = CGBitmapContextCreate(
                    pinned.addressOf(0), w.convert(), h.convert(), 8u, (w * 4).convert(), space,
                    CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
                ) ?: throw FaktelException.InvalidImage("Could not create bitmap context")
                CGContextDrawImage(ctx, CGRectMake(0.0, 0.0, w.toDouble(), h.toDouble()), image)
                CGContextRelease(ctx)
            }
        } finally {
            CGColorSpaceRelease(space)
            CGImageRelease(image)
        }
        return RgbImage.fromRgba(w, h, rgba)
    }
}
