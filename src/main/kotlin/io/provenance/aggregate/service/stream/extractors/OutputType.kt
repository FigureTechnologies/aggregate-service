package io.provenance.aggregate.service.stream.extractors

import java.nio.file.Path

/**
 * A sealed class enumeration that signals how an Extractor produces output.
 */
sealed interface OutputType {
    object None : OutputType                 // No output
    data class FilePath(val path: Path) : OutputType  // Output to a file path:
}