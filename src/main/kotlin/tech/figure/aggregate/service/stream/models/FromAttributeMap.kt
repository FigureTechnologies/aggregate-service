package tech.figure.aggregate.service.stream.models

/**
 * Shorthand for a map of event attributes derived from a list of key/value objects.
 *
 * @see [EncodedBlockchainEvent.toDecodedMap]
 */
typealias AttributeMap = Map<String, String?>

/**
 *  Event attributes are represented as a collection of key-values.
 *
 *  Classes implementing this interface signal that they expect their underlying values to be furnished by an
 *  instance of [AttributeMap].
 *
 * @see [EncodedBlockchainEvent.toDecodedMap]
 */
interface FromAttributeMap {
    val attributes: AttributeMap
}
