package io.provenance.aggregate.service.stream.models.provenance

import io.provenance.aggregate.common.logger
import io.provenance.aggregate.common.models.EncodedBlockchainEvent
import org.slf4j.Logger
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

/**
 * Provides a mapping of Provenance event types to associated classes.
 *
 * For all child classes `C` annotated with `@MappedProvenanceEvent("event")` inheriting from a sealed class `T`, this
 * function will produce a map such that entries of the map will consist of a key specifying the event type string
 * as provided by the `@MappedProvenanceEvent` annotation, and the value will be the constructor of the mapped
 * class `C`.
 */
class EventMapper<T> private constructor(val mapping: Map<String, KFunction<T>>) {

    private val log: Logger = logger()

    /**
     * Given an event type like `provenance.marker.v1.EventMarkerTransfer` or `provenance.attribute.v1.EventAttributeAdd`,
     * look up and potentially create a new instance of the class mapped to that event with the `@MappedProvenanceEvent`
     * annotation.
     */
    fun fromEvent(event: EncodedBlockchainEvent): T? = fromEvent(event.eventType, event.toDecodedMap())

    /**
     * Given an event type like `provenance.marker.v1.EventMarkerTransfer` or `provenance.attribute.v1.EventAttributeAdd`,
     * look up and potentially create a new instance of the class mapped to that event with the `@MappedProvenanceEvent`
     * annotation.
     */
    fun fromEvent(event: String, attributes: AttributeMap): T? {
        return runCatching {
            mapping[event]?.call(attributes)
        }.getOrElse { e ->
            log.error("Couldn't extract event [$event] with arguments $attributes :: $e")
            null
        }
    }

    companion object {
        fun <T : Any> eventToClass(rootClass: KClass<T>): Map<String, KFunction<T>> {
            return rootClass
                .sealedSubclasses
                .mapNotNull { kclass ->
                    kclass.findAnnotation<MappedProvenanceEvent>()
                        ?.let { annotation -> Pair(kclass, annotation) }
                }.mapNotNull { (kclass, annotation) ->
                    kclass.primaryConstructor?.let { ctor -> Pair(ctor, annotation) }
                }
                .associate { (ctor, annotation) -> annotation.name to ctor }
        }

        operator fun <T : Any> invoke(forClass: KClass<T>): EventMapper<T> {
            return EventMapper(eventToClass(forClass))
        }
    }
}
