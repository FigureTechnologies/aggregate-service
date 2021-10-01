package io.provenance.aggregate.service.stream.models.provenance

import io.provenance.aggregate.service.stream.models.EncodedBlockchainEvent

/**
 * Shorthand for a map of event attributes derived from a list of key/value objects.
 *
 * @see EncodedBlockchainEvent.toDecodedMap
 */
typealias AttributeMap = Map<String, String?>

/**
 *  Event attributes are represented as a collection of key-values.
 *
 * @see EncodedBlockchainEvent.toDecodedMap
 */
interface FromAttributeMap {
    val attributes: AttributeMap
}