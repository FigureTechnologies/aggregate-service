package io.provenance.aggregate.service.stream.models.provenance

import io.provenance.aggregate.common.extensions.repeatDecodeBase64

/**
 * Used to transform a delegate value in the context of map-backed delegate by repeatedly base64-decoding the
 * value until a stopping point is reached.
 *
 * @see [repeatDecodeBase64]
 * @example
 *
 *     class EventAttributeAdd(height: Long, val attributes: Map<String, Any?>) : ProvenanceEventAttribute(height) {
 *         val name: String by attributes.transform(::debase64)
 *         val value: String by attributes.transform(::debase64)
 *         ...
 *     }
 */
internal fun debase64(t: String): String = t.repeatDecodeBase64()
