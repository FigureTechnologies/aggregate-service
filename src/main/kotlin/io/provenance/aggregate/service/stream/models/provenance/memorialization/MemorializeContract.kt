package io.provenance.aggregate.service.stream.models.provenance.memorialization

import io.provenance.aggregate.common.extensions.transform
import io.provenance.aggregate.service.stream.models.provenance.EventMapper
import io.provenance.aggregate.service.stream.models.provenance.FromAttributeMap
import io.provenance.aggregate.service.stream.models.provenance.MappedProvenanceEvent
import io.provenance.aggregate.service.stream.models.provenance.debase64

/**
 * A sealed classes enumeration which models the Provenance event attributes:
 *
 * - `message`
 */
sealed class MemorializeContract: FromAttributeMap {

    companion object {
        val mapper = EventMapper(MemorializeContract::class)
    }

    abstract fun isMemorializeRequest(): Boolean

    /**
     * Read action attributes for p8e_memorialize_contract_request as these charge a
     * gas fee for execution.
     */
    @MappedProvenanceEvent("message")
    class Message(override val attributes: Map<String, String?>) : MemorializeContract() {
        val action: String? by attributes.transform(::debase64)

        override fun isMemorializeRequest(): Boolean {
            return action == "p8e_memorialize_contract_request"
        }
    }

}
