package io.provenance.aggregate.service.stream.models.provenance.cosmos

import io.provenance.aggregate.service.extensions.transform
import io.provenance.aggregate.service.stream.models.provenance.EventMapper
import io.provenance.aggregate.service.stream.models.provenance.FromAttributeMap
import io.provenance.aggregate.service.stream.models.provenance.MappedProvenanceEvent
import io.provenance.aggregate.service.stream.models.provenance.debase64

/**
 * A sealed classes enumeration which models the Provenance event attributes:
 *
 * - `transfer`
 */
sealed class Tx() : FromAttributeMap {

    companion object {
        val mapper = EventMapper(Tx::class)
    }

    /**
     * Represents the transfer of coin from one account to another.
     *
     * @see https://docs.cosmos.network/master/core/proto-docs.html#cosmos.bank.v1beta1.MsgSend
     */
    @MappedProvenanceEvent("transfer")
    class Transfer(override val attributes: Map<String, String?>) : Tx() {
        val recipient: String by attributes.transform(::debase64)
        val sender: String by attributes.transform(::debase64)
        val amountAndDenom: String by attributes.transform("amount", ::debase64)
    }
}