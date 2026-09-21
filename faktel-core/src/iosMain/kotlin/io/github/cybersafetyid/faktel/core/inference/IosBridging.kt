@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.github.cybersafetyid.faktel.core.inference

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

// Helpers for Swift/Obj-C inference backends: moving big buffers element-by-element through Kotlin array
// wrappers is prohibitively slow, so these copy with a single memcpy.

/** Copies the bytes into a new `NSData`. */
public fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }

/** Copies the bytes into a new Kotlin `ByteArray` (e.g. a model or pixel buffer held by Swift as `Data`). */
public fun NSData.toByteArray(): ByteArray {
    val out = ByteArray(length.toInt())
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length.convert()) }
    return out
}

/** Copies the tensor's float32 values (native byte order) into a new `NSData`. */
public fun Tensor.toNSData(): NSData =
    if (data.isEmpty()) NSData() else data.usePinned { NSData.create(bytes = it.addressOf(0), length = (data.size * 4).convert()) }

/**
 * Builds a [Tensor] from float32 bytes.
 * @param shape dimension sizes; their product times 4 must equal `data.length`
 */
public fun tensorFromNSData(shape: List<Int>, data: NSData): Tensor {
    val floats = FloatArray(data.length.toInt() / 4)
    if (floats.isNotEmpty()) floats.usePinned { memcpy(it.addressOf(0), data.bytes, data.length.convert()) }
    return Tensor(shape.toIntArray(), floats)
}
