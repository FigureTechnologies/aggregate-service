package io.provenance.aggregate.service.stream.models.provenance.attribute

import io.provenance.aggregate.service.extensions.*
import io.provenance.aggregate.service.stream.models.provenance.MappedProvenanceEvent
import io.provenance.aggregate.service.stream.models.provenance.EventMapper
import io.provenance.aggregate.service.stream.models.provenance.FromAttributeMap
import io.provenance.aggregate.service.stream.models.provenance.debase64

private fun toEventTypeEnum(t: String): EventAttributeType = EventAttributeType.valueOf(t)

sealed class EventAttribute() : FromAttributeMap {

    companion object {
        /**
         * Maps Provenance events like "provenance.attribute.v1.EventAttributeAdd" to constructors for the
         * `ProvenanceTxEvent` sealed class family.
         */
        val mapper = EventMapper(EventAttribute::class)
    }

    abstract fun toEventRecord(): ConsolidatedEvent

    override fun toString() = toEventRecord().toString()

    /**
     * A container class that is capable of representing the consolidated union of data contained int the
     * `EventAttributeAdd`, `EventAttributeUpdate`, `EventAttributeDelete`, and `EventAttributeDeleteDistinct` classes.
     */
    data class ConsolidatedEvent(
        val name: String,
        val value: String?,
        val updatedValue: String?,
        val type: String?,
        val updatedType: String?,
        val account: String,
        val owner: String
    )

    /**
     * Event emitted when attribute is added.
     * @see https://github.com/provenance-io/provenance/blob/v1.7.1/docs/proto-docs.md#eventattributeadd
     */
    @MappedProvenanceEvent("provenance.attribute.v1.EventAttributeAdd")
    class Add(override val attributes: Map<String, String?>) : EventAttribute() {
        val name: String by attributes.transform(::debase64)
        val value: String by attributes.transform(::debase64)
        val type: EventAttributeType by attributes.transform { t: String ->
            toEventTypeEnum(debase64(t))
        }
        val account: String by attributes.transform(::debase64)
        val owner: String by attributes.transform(::debase64)

        override fun toEventRecord(): ConsolidatedEvent = ConsolidatedEvent(
            name = name,
            value = value,
            updatedValue = null,
            type = type.name,
            updatedType = null,
            account = account,
            owner = owner
        )
    }

    /**
     * Event emitted when attribute is updated
     * @see https://github.com/provenance-io/provenance/blob/v1.7.1/docs/proto-docs.md#eventattributeupdate
     */
    @MappedProvenanceEvent("provenance.attribute.v1.EventAttributeUpdate")
    class Update(override val attributes: Map<String, String?>) : EventAttribute() {
        val name: String by attributes.transform(::debase64)
        val originalValue: String by attributes.transform("original_value", ::debase64)
        val originalType: EventAttributeType by attributes.transform("original_type") { t: String ->
            toEventTypeEnum(debase64(t))
        }
        val updateValue: String by attributes.transform("update_value", ::debase64)
        val updateType: EventAttributeType by attributes.transform("update_type") { t: String ->
            toEventTypeEnum(debase64(t))
        }
        val account: String by attributes.transform(::debase64)
        val owner: String by attributes.transform(::debase64)

        override fun toEventRecord(): ConsolidatedEvent = ConsolidatedEvent(
            name = name,
            value = originalValue,
            updatedValue = updateValue,
            type = originalType.name,
            updatedType = updateType.name,
            account = account,
            owner = owner
        )
    }

    /**
     * Event emitted when attribute is deleted.
     * @see https://github.com/provenance-io/provenance/blob/v1.7.1/docs/proto-docs.md#eventattributedelete
     */
    @MappedProvenanceEvent("provenance.attribute.v1.EventAttributeDelete")
    class Delete(override val attributes: Map<String, String?>) : EventAttribute() {
        val name: String by attributes.transform(::debase64)
        val account: String by attributes.transform(::debase64)
        val owner: String by attributes.transform(::debase64)

        override fun toEventRecord(): ConsolidatedEvent = ConsolidatedEvent(
            name = name,
            value = null,
            updatedValue = null,
            type = null,
            updatedType = null,
            account = account,
            owner = owner
        )
    }

    /**
     * Event emitted when attribute is deleted with matching value.
     * @see https://github.com/provenance-io/provenance/blob/v1.7.1/docs/proto-docs.md#eventattributedistinctdelete
     */
    @MappedProvenanceEvent("provenance.attribute.v1.EventAttributeDistinctDelete")
    class DistinctDelete(override val attributes: Map<String, String?>) : EventAttribute() {
        val name: String by attributes.transform(::debase64)
        val value: String by attributes.transform(::debase64)
        val attributeType: EventAttributeType by attributes.transform("attribute_type") { t: String ->
            toEventTypeEnum(debase64(t))
        }
        val account: String by attributes.transform(::debase64)
        val owner: String by attributes.transform(::debase64)

        override fun toEventRecord(): ConsolidatedEvent = ConsolidatedEvent(
            name = name,
            value = value,
            updatedValue = null,
            type = attributeType.name,
            updatedType = null,
            account = account,
            owner = owner
        )
    }
}