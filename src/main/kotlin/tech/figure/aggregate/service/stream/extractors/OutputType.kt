package tech.figure.aggregate.service.stream.extractors

import java.nio.file.Path

/**
 * A sealed class enumeration that signals how an Extractor produces output.
 */
sealed interface OutputType {
    /**
     * No explicit output
     */
    object None : OutputType

    /**
     * File output, with optional metadata attached to it.
     *
     * @property path The path the file was written to.
     * @property metadata Optional metadata to associate with the file.
     */
    data class FilePath(val path: Path, val metadata: Map<String, String>? = null) : OutputType
}
