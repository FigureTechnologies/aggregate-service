package io.provenance.aggregate.service.extensions

import com.google.common.io.BaseEncoding
import com.timgroup.statsd.StatsDClient
import io.provenance.aggregate.service.utils.sha256
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// === String methods ==================================================================================================

/**
 * Remove surrounding quotation marks fro a string.
 */
fun String.stripQuotes(): String = this.removeSurrounding("\"")

/**
 * Base64 decode a string.
 */
fun String.decodeBase64(): String = BaseEncoding.base64().decode(this).decodeToString()

/**
 * Compute a hex-encoded (printable) version of a SHA-256 encoded string.
 */
fun String.hash(): String = sha256(BaseEncoding.base64().decode(this)).toHexString()

// === ByteArray methods ===============================================================================================

/**
 * Compute a hex-encoded (printable) version of a SHA-256 encoded byte array.
 */
fun ByteArray.toHexString(): String = BaseEncoding.base16().encode(this)

// === Delegate methods ================================================================================================

/**
 * Provide an alternate name for a delegate property.
 *
 * @example
 *
 *   data class EventUpdate(map: Map<String, Any?>) {
 *     val name: String by map
 *     val original_value: String by map
 *     val update_value: String by map
 *   )
 *
 * becomes
 *
 *   data class EventUpdate(map: Map<String, Any?>) {
 *     val name: String by map
 *     val originalValue: String by rename(map, "original_value")
 *     val updateValue: String by rename(map, "update_value")
 *   )
 *
 * @see https://kotlinlang.org/docs/delegated-properties.html#property-delegate-requirements
 * Note: adapted from https://stackoverflow.com/a/36602770
 */
fun <T, V> transform(properties: Map<String, Any?>, key: String): ReadOnlyProperty<T, V> =
    ReadOnlyProperty { _: T, _: KProperty<*> -> properties[key]!! as V }

@JvmName("renameDelegateOnPropertyMap")
fun <T, V> Map<String, Any?>.transform(key: String): ReadOnlyProperty<T, V> = transform(this, key)

/**
 * Apply a transformation to a delegate property.
 *
 * @see https://kotlinlang.org/docs/delegated-properties.html#property-delegate-requirements
 * Note: adapted from https://stackoverflow.com/a/36602770
 */
fun <T, U, V> transform(properties: Map<String, Any?>, f: (U) -> V): ReadOnlyProperty<T, V> =
    ReadOnlyProperty { _: T, property: KProperty<*> -> f(properties[property.name]!! as U) }

@JvmName("mapDelegateOnPropertyMap")
fun <T, U, V> Map<String, Any?>.transform(f: (U) -> V): ReadOnlyProperty<T, V> = transform(this, f)

/**
 * Rename and apply a transformation to a delegate property.
 *
 * @see https://kotlinlang.org/docs/delegated-properties.html#property-delegate-requirements
 * Note: adapted from https://stackoverflow.com/a/36602770
 */
fun <T, U, V> transform(properties: Map<String, Any?>, key: String, f: (U) -> V): ReadOnlyProperty<T, V> =
    ReadOnlyProperty { _: T, _: KProperty<*> -> f(properties[key]!! as U) }

@JvmName("mapAndRenameDelegateOnPropertyMap")
fun <T, U, V> Map<String, Any?>.transform(key: String, f: (U) -> V): ReadOnlyProperty<T, V> = transform(this, key, f)

fun StatsDClient.recordMaxBlockHeight(height: Long) = this.gauge("block_height", height)
