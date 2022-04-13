Schema definition
=================

_Note: The hash value of a row is computed by the concatenation of the column values (barring `hash` itself) in the
order they're defined. If a value is null, it will be skipped._

tx_event_attributes
-------------------

### Name

`tx_event_attributes.csv`

### Description

Provides a consolidated view of the `provenance.attribute.v1.EventAttributeAdd`
, `provenance.attribute.v1.EventAttributeUpdate`,
`provenance.attribute.v1.EventAttributeDelete`, and `provenance.attribute.v1.EventAttributeDistinctDelete` Provenance
`attribute` transaction events combined together.

### Structure

| Column           | Type           | Nullable? |
| ---------------- | -------------- | --------- |
| hash             | string         | false     |
| event_type       | string         | false     |
| block_height     | integer        | false     |
| block_timestamp  | string         | true      |
| name             | string         | false     |
| value            | string         | true      |
| type             | string (enum)  | true      |
| account          | string         | false     |
| owner            | string         | false     |

where the values of `type` is an enumeration with the following values (as defined
in https://github.com/provenance-io/provenance/blob/v1.7.1/docs/proto-docs.md#attributetype)

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

tx_marker_supply
----------------

### Name

`tx_marker_supply.csv`

### Description

Provides a consolidated view of the `provenance.marker.v1.EventMarkerActivate`, `provenance.marker.v1.EventMarkerAdd`,
`provenance.marker.v1.EventMarkerBurn`, `provenance.marker.v1.EventMarkerCancel`,
`provenance.marker.v1.EventMarkerDelete`, `provenance.marker.v1.EventMarkerFinalize`,
`provenance.marker.v1.EventMarkerMint`, `provenance.marker.v1.EventMarkerSetDenomMetadata`, and
`provenance.marker.v1.EventMarkerWithdraw` Provenance `marker` transaction events combined. Together, these events
relate to the supply of a marker on the Provenance network.

### Structure

| Column               | Type   | Nullable? |
| -------------------- | -------| --------- |
| hash                 | string | false     |
| event_type           | string | false     |
| block_height         | integer| false     |
| block_timestamp      | string | true      |
| denom                | string | true      |
| amount               | string | true      |
| administrator        | string | true      |
| to_address           | string | true      |
| from_address         | string | true      |
| metadata_base        | string | true      |
| metadata_description | string | true      |
| metadata_display     | string | true      |
| metadata_denom_units | string | true      |
| metadata_name        | string | true      |
| metadata_symbol      | string | true      |

tx_marker_transfer
------------------

### Name

`tx_marker_transfer.csv`

### Description

Records instances of the Provenance `provenance.marker.v1.EventMarkerTransfer` transaction event. This event corresponds
to the transfer of a marker between accounts on the network.

### Structure

| Column          | Type    | Nullable? |
| --------------- | ------- | --------- |
| hash            | string  | false     |
| event_type      | string  | false     |
| block_height    | integer | false     |
| block_timestamp | string  | true      |
| amount          | string  | false     |
| denom           | string  | false     |
| administrator   | string  | false     |
| to_address      | string  | false     |
| from_address    | string  | false     |


tx_coin_transfer
------------------

### Name

`tx_coin_transfer.csv`

### Description

Records instances of the Provenance `transfer` transaction event. This event corresponds to the transfer of coins between accounts on the network.

### Structure


| Column          | Type    | Nullable? |
|-----------------| ------- |-----------|
| hash            | string  | false     |
| event_type      | string  | false     |
| block_height    | integer | false     |
| block_timestamp | string  | true      |
| tx_hash         | string  | true      |
| recipient       | string  | true      |
| sender          | string  | true      |
| amount          | string  | true      |
| denom           | string  | true      |

tx_errors
------------------

### Name

`tx_errors.csv`

### Description

Records instances of errors during any Provenance `transfer` transaction event. 

### Structure

| Column          | Type    | Nullable? |
|-----------------| ------- |-----------|
| hash            | string  | false     |
| block_height    | string  | false     |
| block_timestamp | integer | false     |
| error_code      | string  | true      |
| info            | string  | true      |

tx_fees
------------------

### Name

`tx_fees.csv`

### Description

Records fees incurred during any Provenance `transfer` transaction event. 

### Structure

| Column          | Type    | Nullable? |
|-----------------|---------|-----------|
| hash            | string  | false     |
| tx_hash         | string  | false     |
| block_height    | integer | false     |
| block_timestamp | string  | true      |
| fee             | string  | true      |
| fee_denom       | string  | true      |
| sender          | string  | true      |

nycb_usdf_balances
------------------

### Name

`nycb_usdf_balances.csv`

### Description

Records NYCB USDF transfers 

### Structure


| Column    | Type    | Nullable? |
|-----------|---------|-----------|
| account   | string  | false     |
| date      | string  | false     |
| balance   | integer | true      |
| timestamp | string  | true      |
| height    | string  | true      |
