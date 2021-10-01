package io.provenance.aggregate.service.stream.extractors

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Abstract extractor that writes output to a file.
 */
abstract class FileExtractor(override val name: String) : Extractor, Closeable, AutoCloseable {

    protected val outputFile: Path = Files.createTempFile("${name}-", ".csv")

    protected val outputStream: OutputStream =
        BufferedOutputStream(Files.newOutputStream(outputFile, StandardOpenOption.APPEND, StandardOpenOption.WRITE))

    override fun output(): OutputType = OutputType.FilePath(outputFile)

    override fun close() {
        try {
            Files.deleteIfExists(outputFile)
        } catch (e: IOException) {
        }
    }
}