package io.provenance.aggregate.service.stream.models

import io.provenance.aggregate.service.extensions.*
import io.provenance.aggregate.service.logger
import io.provenance.aggregate.service.writer.Record
import io.provenance.aggregate.service.writer.RecordWriter
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

private fun debase64(t: String): String = t.decodeBase64().stripQuotes()

private fun toEventTypeEnum(t: String): ProvenanceEventAttributeType = ProvenanceEventAttributeType.valueOf(t)

sealed class ProvenanceEventAttribute(val height: Long) {

    data class EventRecord(
        val height: Long,
        val name: String,
        val value: String?,
        val updatedValue: String?,
        val type: String?,
        val updatedType: String?,
        val account: String,
        val owner: String
    )

    companion object {

        private val log = logger()

        // Maps Provenance events like "provenance.attribute.v1.EventAttributeAdd" to constructors for the
        // `ProvenanceTxEvent` sealed class family.
        private val mapping: Map<String, KFunction<ProvenanceEventAttribute>> =
            ProvenanceEventAttribute::class.sealedSubclasses
                .mapNotNull { kclass ->
                    kclass.findAnnotation<MappedProvenanceEvent>()?.let { annotation -> Pair(kclass, annotation) }
                }.associate { (kclass, annotation) -> annotation.name to kclass.primaryConstructor!! }

        /**
         * Given an Provenance event type string as it would appear in a transaction event payload,
         * e.g. "provenance.attribute.v1.EventAttributeAdd", attempt to create an instance of TxEvent.
         */
        @JvmStatic
        fun createFromEventType(event: String, vararg args: Any?): ProvenanceEventAttribute? {
            return runCatching {
                mapping[event]?.call(*args)
            }.getOrElse { e ->
                log.error("Couldn't extract event [$event] with arguments ${args} :: $e")
                null
            }
        }
    }

    abstract fun toEventRecord(): EventRecord

    /**
     * Event emitted when attribute is added.
     * @see https://github.com/provenance-io/provenance/blob/v1.7.1/docs/proto-docs.md#eventattributeadd
     */
    @MappedProvenanceEvent("provenance.attribute.v1.EventAttributeAdd")
    class EventAttributeAdd(height: Long, val attributes: Map<String, Any?>) : ProvenanceEventAttribute(height) {

        val name: String by attributes.transform(::debase64)
        val value: String by attributes // leave encoded
        val type: ProvenanceEventAttributeType by attributes.transform { t: String ->
            toEventTypeEnum(debase64(t))
        }
        val account: String by attributes.transform(::debase64)
        val owner: String by attributes.transform(::debase64)

        override fun toEventRecord(): EventRecord = EventRecord(
            height = height,
            name = name,
            value = value,
            updatedValue = null,
            type = type.name,
            updatedType = null,
            account = account,
            owner = owner
        )

        override fun toString() = toEventRecord().toString()
    }

    /**
     * Event emitted when attribute is updated
     * @see https://github.com/provenance-io/provenance/blob/v1.7.1/docs/proto-docs.md#eventattributeupdate
     */
    @MappedProvenanceEvent("provenance.attribute.v1.EventAttributeUpdate")
    class EventAttributeUpdate(height: Long, attributes: Map<String, Any?>) : ProvenanceEventAttribute(height) {

        val name: String by attributes.transform(::debase64)
        val originalValue: String by attributes.transform("original_value")
        val originalType: ProvenanceEventAttributeType by attributes.transform("original_type") { t: String ->
            toEventTypeEnum(debase64(t))
        }
        val updateValue: String by attributes.transform("update_value")
        val updateType: ProvenanceEventAttributeType by attributes.transform("update_type") { t: String ->
            toEventTypeEnum(debase64(t))
        }
        val account: String by attributes.transform(::debase64)
        val owner: String by attributes.transform(::debase64)

        override fun toEventRecord(): EventRecord = EventRecord(
            height = height,
            name = name,
            value = originalValue,
            updatedValue = updateValue,
            type = originalType.name,
            updatedType = updateType.name,
            account = account,
            owner = owner
        )

        override fun toString() = toEventRecord().toString()
    }

    /**
     * Event emitted when attribute is deleted.
     * @see https://github.com/provenance-io/provenance/blob/v1.7.1/docs/proto-docs.md#eventattributedelete
     */
    @MappedProvenanceEvent("provenance.attribute.v1.EventAttributeDelete")
    class EventAttributeDelete(height: Long, attributes: Map<String, Any?>) : ProvenanceEventAttribute(height) {

        val name: String by attributes.transform(::debase64)
        val account: String by attributes.transform(::debase64)
        val owner: String by attributes.transform(::debase64)

        override fun toEventRecord(): EventRecord = EventRecord(
            height = height,
            name = name,
            value = null,
            updatedValue = null,
            type = null,
            updatedType = null,
            account = account,
            owner = owner
        )

        override fun toString() = toEventRecord().toString()
    }

    /**
     * Event emitted when attribute is deleted with matching value
     * @see https://github.com/provenance-io/provenance/blob/v1.7.1/docs/proto-docs.md#eventattributedistinctdelete
     */
    @MappedProvenanceEvent("provenance.attribute.v1.EventAttributeDistinctDelete")
    class AttributeDistinctDelete(height: Long, attributes: Map<String, Any?>) : ProvenanceEventAttribute(height) {

        val name: String by attributes.transform(::debase64)
        val value: String by attributes // leave encoded
        val attributeType: ProvenanceEventAttributeType by attributes.transform("attribute_type") { t: String ->
            toEventTypeEnum(debase64(t))
        }
        val account: String by attributes.transform(::debase64)
        val owner: String by attributes.transform(::debase64)

        override fun toEventRecord(): EventRecord = EventRecord(
            height = height,
            name = name,
            value = value,
            updatedValue = null,
            type = attributeType.name,
            updatedType = null,
            account = account,
            owner = owner
        )

        override fun toString() = toEventRecord().toString()
    }
}