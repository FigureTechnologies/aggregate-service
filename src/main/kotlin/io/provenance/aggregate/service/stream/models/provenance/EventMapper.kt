package io.provenance.aggregate.service.stream.models.provenance

import io.provenance.aggregate.service.logger
import io.provenance.aggregate.service.stream.models.EncodedBlockchainEvent
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
     * look up and potentially create a new instance of the class mapped to that event with the [MappedProvenanceEvent]
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
        /**
         * A utility function that builds a map out of a sealed class family, where the inheriting child classes are
         * annotated with [MappedProvenanceEvent].
         *
         * Example:
         *
         * Assume we have a sealed class `Family` and some inheriting classes:
         *
         * ```
         * sealed interface Family {}
         *
         *   @MappedProvenanceEvent("some.event.Foo")
         *   data class Foo : Family
         *
         *   @MappedProvenanceEvent("some.event.Bar")
         *   data class Bar : Family
         *
         *   @MappedProvenanceEvent("some.event.Baz")
         *   data class Baz : Family
         * ```
         *
         * Using [eventToClass] with `Family` produces a mapping of event names to constructors for classes mapped
         * to said event strings.
         *
         * ```
         * val map = eventToClass(Family::class)
         * // map = {
         *   "some.event.Foo" to KFunction<Foo>,
         *   "some.event.Bar" to KFunction<Bar>,
         *   "some.event.Baz" to KFunction<Baz>
         * }
         * ```
         */
        private fun <T : Any> eventToClass(rootClass: KClass<T>): Map<String, KFunction<T>> {
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

        /**
         * Create a new event mapper for a given class.
         *
         * Example usage:
         *
         * ```
         * val event: TxEvent = ...
         * val mapper = EventMapper(EventAttribute::class)
         * val attribute: EventAttribute? = mapper.fromEvent(event)
         * ```
         *
         * @param T The target type to map the tx event to.
         * @property forClass The target class the event will be translated to.
         * @return An instance of the mapped type.
         */
        operator fun <T : Any> invoke(forClass: KClass<T>): EventMapper<T> {
            return EventMapper(eventToClass(forClass))
        }
    }
}