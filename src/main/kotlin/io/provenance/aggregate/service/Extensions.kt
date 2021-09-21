package io.provenance.aggregate.service.extensions

import com.google.common.io.BaseEncoding
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

fun String.decodeBase64(): String = BaseEncoding.base64().decode(this).decodeToString()

fun String.hash(): String = sha256(BaseEncoding.base64().decode(this)).toHexString()

fun ByteArray.toHexString(): String = BaseEncoding.base16().encode(this)

fun sha256(input: ByteArray?): ByteArray =
    try {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.digest(input)
    } catch (e: NoSuchAlgorithmException) {
        throw RuntimeException("Couldn't find a SHA-256 provider", e)
    }
