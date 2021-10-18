package io.provenance.aggregate.service.stream.models.provenance

/**
 * Annotate a class with this to associate it with a Provenance event.
 *
 * Example:
 *
 * ```
 * @MappedProvenanceEvent("provenance.attribute.v1.EventAttributeAdd")
 * class AttributeAdd(height: Long, val map: Map<String, Any?>) {
 *   ...
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
annotation class MappedProvenanceEvent(val name: String)