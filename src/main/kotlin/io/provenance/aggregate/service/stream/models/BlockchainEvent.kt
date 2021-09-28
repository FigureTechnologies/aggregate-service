package io.provenance.aggregate.service.stream.models

/**
 * Common interface for various blockchain event types.
 */
interface BlockchainEvent {
    fun getType(): String
}