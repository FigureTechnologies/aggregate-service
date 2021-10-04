
event_attributes
----------------

| Column                 | Type           | Nullable? |
| ---------------------- | -------------- | --------- |
| event_type             | string         | false     |
| block_height           | integer        | false     |
| block_timestamp        | datetime       | false     |
| name                   | string         | false     |
| value                  | string         | false     |
| updated_value          | string         | true      |
| attribute_type         | string (enum)  | false     |
| updated_attribute_type | string (enum)  | true      |
| account                | string         | false     |
| owner                  | string         | false     |

where the values of `value_type` and `updated_value_type` are an enumeration with the
following values (as defined in https://github.com/provenance-io/provenance/blob/v1.7.1/docs/proto-docs.md#attributetype)

| Name                         | Number | Description                                                                         |
| ---------------------------- | ------ | ----------------------------------------------------------------------------------- |
| `ATTRIBUTE_TYPE_UNSPECIFIED` | 0      | Defines an unknown/invalid type                                                     |
| `ATTRIBUTE_TYPE_UUID`        | 1      | Defines an attribute value that contains a string value representation of a v4 UUID |
| `ATTRIBUTE_TYPE_JSON`        | 2      | Defines an attribute value that contains a byte string containing json data         |
| `ATTRIBUTE_TYPE_STRING`      | 3      | Defines an attribute value that contains a generic string value                     |
| `ATTRIBUTE_TYPE_URI`         | 4      | Defines an attribute value that contains a URI                                      |
| `ATTRIBUTE_TYPE_INT`         | 5      | Defines an attribute value that contains an integer (cast as int64)                 |
| `ATTRIBUTE_TYPE_FLOAT`       | 6      | Defines an attribute value that contains a float                                    |
| `ATTRIBUTE_TYPE_PROTO`       | 7      | Defines an attribute value that contains a serialized proto value in bytes          |
| `ATTRIBUTE_TYPE_BYTES`       | 8      | Defines an attribute value that contains an untyped array of bytes                  |