package io.provenance.aggregate.service.extensions

import com.google.common.io.BaseEncoding
import com.timgroup.statsd.StatsDClient
import io.provenance.aggregate.service.utils.sha256
import org.apache.commons.lang3.StringUtils
import org.json.JSONArray
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// === String methods ==================================================================================================

/**
 * Remove surrounding quotation marks from a string.
 */
fun String.stripQuotes(): String = this.removeSurrounding("\"")

/**
 * Base64 decode a string. In the event of failure, the original string is returned.
 */
fun String.decodeBase64(): String =
    runCatching { BaseEncoding.base64().decode(this).decodeToString() }.getOrDefault(this)

/**
 * Checks if the string contains only ASCII printable characters.
 */
fun String.isAsciiPrintable(): Boolean = StringUtils.isAsciiPrintable(this)

/**
 * Decodes a string repeatedly base64 encoded, terminating when:
 *
 * - the decoded string stops changing or
 * - the maximum number of iterations is reached
 * - or the decoded string is no longer ASCII printable
 *
 * In the event of failure, the last successfully decoded string is returned.
 */
fun String.repeatDecodeBase64(): String {
    var s: String = this.toString() // copy
    var t: String = s.decodeBase64().stripQuotes()
    repeat(10) {
        if (s == t || !t.isAsciiPrintable()) {
            return s
        }
        s = t
        t = t.decodeBase64().stripQuotes()

    }
    return s
}

/**
 * Compute a hex-encoded (printable) SHA-256 encoded string, from a base64 encoded string.
 */
fun String.hash(): String = sha256(BaseEncoding.base64().decode(this)).toHexString()

// === Date/time methods ===============================================================================================

/**
 * Generate an ISO8601 string from the date.
 */
fun OffsetDateTime.toISOString() = this.format(DateTimeFormatter.ISO_DATE_TIME).toString()

// === ByteArray methods ===============================================================================================

/**
 * Compute a hex-encoded (printable) version of a SHA-256 encoded byte array.
 */
fun ByteArray.toHexString(): String = BaseEncoding.base16().encode(this)

// === Delegate methods ================================================================================================

/**
 * Apply a transformation to a delegate property.
 *
 * @example
 *
 *   data class EventUpdate(map: Map<String, Any?>) {
 *     val name: String by map
 *     val originalValue: String by map.transform { it.lowercase() }
 *     val updateValue: String by map.transform(::encode)
 *   )
 *
 * @see https://kotlinlang.org/docs/delegated-properties.html#property-delegate-requirements
 * Note: adapted from https://stackoverflow.com/a/36602770
 */
@Suppress("UNCHECKED_CAST")
fun <T, U, V> transform(properties: Map<String, Any?>, f: (U) -> V): ReadOnlyProperty<T, V> =
    ReadOnlyProperty { _: T, property: KProperty<*> -> f(properties[property.name]!! as U) }

@JvmName("mapDelegateOnPropertyMap")
fun <T, U, V> Map<String, Any?>.transform(f: (U) -> V): ReadOnlyProperty<T, V> = transform(this, f)

/**
 * Use an alternative name to look up a property and apply a transformation to a delegated value.
 *
 * @example
 *
 *   data class EventUpdate(map: Map<String, Any?>) {
 *     val name: String by map
 *     val originalValue: String by map.transform("original_value") { it.lowercase() }
 *     val updateValue: String by map.transform("update_value", ::encode)
 *   )
 *
 * @see https://kotlinlang.org/docs/delegated-properties.html#property-delegate-requirements
 * Note: adapted from https://stackoverflow.com/a/36602770
 */
@Suppress("UNCHECKED_CAST")
fun <T, U, V> transform(properties: Map<String, Any?>, otherName: String, f: (U) -> V): ReadOnlyProperty<T, V> =
    ReadOnlyProperty { _: T, _: KProperty<*> -> f(properties[otherName]!! as U) }

@JvmName("mapAndRenameDelegateOnPropertyMap")
fun <T, U, V> Map<String, Any?>.transform(otherName: String, f: (U) -> V): ReadOnlyProperty<T, V> =
    transform(this, otherName, f)

/**
 * Use an alternative name (alias) to look up a property.
 *
 * @example
 *
 *   data class EventUpdate(map: Map<String, Any?>) {
 *     val name: String by map
 *     val originalValue: String by map.alias("original_value")
 *     val updateValue: String by map.alias("update_value")
 *   )
 */
fun <T, V> Map<String, Any?>.alias(otherName: String): ReadOnlyProperty<T, V> =
    transform<T, V, V>(this, otherName) { it }

// === Data Dog ========================================================================================================

fun StatsDClient.recordMaxBlockHeight(height: Long) = runCatching {
    this.gauge("block_height", height)
}
