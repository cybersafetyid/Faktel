package io.github.cybersafetyid.faktel.core.inference

import android.content.Context

/** Reads a model bundled in the app's `assets/` directory. */
public fun Context.readModelAsset(path: String): ByteArray = assets.open(path).use { it.readBytes() }
