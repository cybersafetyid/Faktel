@file:OptIn(ExperimentalForeignApi::class)

package io.github.cybersafetyid.faktel.core.inference

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

/**
 * Reads a model bundled with the app, e.g. `readBundleModel("face_detection_yunet_2023mar", "onnx")`.
 * @throws IllegalStateException if the resource is missing from [bundle]
 */
public fun readBundleModel(name: String, extension: String, bundle: NSBundle = NSBundle.mainBundle): ByteArray {
    val path = bundle.pathForResource(name, extension) ?: error("Bundle resource not found: $name.$extension")
    val data = NSData.dataWithContentsOfFile(path) ?: error("Could not read $path")
    val out = ByteArray(data.length.toInt())
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), data.bytes, data.length.convert()) }
    return out
}
