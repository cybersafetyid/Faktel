package io.github.cybersafetyid.faktel.core.inference

import java.io.File

/** Reads a model from the classpath (e.g. `src/main/resources`), or fails with a descriptive error. */
public fun readModelResource(path: String, loader: ClassLoader = Thread.currentThread().contextClassLoader): ByteArray =
    (loader.getResourceAsStream(path) ?: error("Model resource not found on classpath: $path")).use { it.readBytes() }

/** Reads a model from the file system. */
public fun readModelFile(path: String): ByteArray = File(path).readBytes()
