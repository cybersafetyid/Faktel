package io.github.cybersafetyid.faktel.core

/** Base class for all exceptions thrown by Faktel. Business outcomes (e.g. "no face found") are never exceptions. */
public sealed class FaktelException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    /** The bytes handed to an image decoder are not a supported image. */
    public class InvalidImage(message: String, cause: Throwable? = null) : FaktelException(message, cause)

    /** A model file could not be loaded, or does not match the shape/IO contract Faktel expects. */
    public class ModelLoad(message: String, cause: Throwable? = null) : FaktelException(message, cause)

    /** The inference backend failed while running a model. */
    public class Inference(message: String, cause: Throwable? = null) : FaktelException(message, cause)
}
