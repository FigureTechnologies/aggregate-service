package tech.figure.aggregate.service.stream.models.attribute

/**
 * As defined in https://github.com/provenance-io/provenance/blob/v1.7.1/docs/proto-docs.md#attributetype
 */
enum class EventAttributeType {
    ATTRIBUTE_TYPE_UNSPECIFIED, // Defines an unknown/invalid type
    ATTRIBUTE_TYPE_UUID,        // Defines an attribute value that contains a string value representation of a V4 uuid
    ATTRIBUTE_TYPE_JSON,        // Defines an attribute value that contains a byte string containing json data
    ATTRIBUTE_TYPE_STRING,      // Defines an attribute value that contains a generic string value
    ATTRIBUTE_TYPE_URI,         // Defines an attribute value that contains a URI
    ATTRIBUTE_TYPE_INT,         // Defines an attribute value that contains an integer (cast as int64)
    ATTRIBUTE_TYPE_FLOAT,       // Defines an attribute value that contains a float
    ATTRIBUTE_TYPE_PROTO,       // Defines an attribute value that contains a serialized proto value in bytes
    ATTRIBUTE_TYPE_BYTES        // Defines an attribute value that contains an untyped array of bytes
}
