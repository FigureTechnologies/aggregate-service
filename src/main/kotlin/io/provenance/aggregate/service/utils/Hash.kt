package io.provenance.aggregate.service.utils

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * Compute a hex-encoded (printable) version of a SHA-256 encoded string.
 */
fun sha256(input: ByteArray?): ByteArray =
    try {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.digest(input)
    } catch (e: NoSuchAlgorithmException) {
        throw RuntimeException("Couldn't find a SHA-256 provider", e)
    }

/**
 * Compute a hex-encoded (printable) version of a SHA-256 encoded string from a series of byte arrays.
 */
fun sha256(vararg inputs: String?): ByteArray = sha256(inputs.asIterable())

fun sha256(inputs: Iterable<String?>): ByteArray =
    sha256(inputs.filterNotNull().joinToString("").toByteArray())