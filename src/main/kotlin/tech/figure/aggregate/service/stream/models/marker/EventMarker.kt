package tech.figure.aggregate.service.stream.models.marker

import tech.figure.aggregate.common.transform
import tech.figure.aggregate.service.stream.models.EventMapper
import tech.figure.aggregate.service.stream.models.MappedProvenanceEvent
import tech.figure.aggregate.service.stream.models.debase64
import tech.figure.aggregate.service.stream.models.FromAttributeMap

/**
 * A sealed classes enumeration which models the Provenance event attributes:
 *
 * - `provenance.marker.v1.EventMarkerActivate`
 * - `provenance.marker.v1.EventMarkerBurn`
 * - `provenance.marker.v1.EventMarkerCancel`
 * - `provenance.marker.v1.EventMarkerDelete`
 * - `provenance.marker.v1.EventMarkerFinalize`
 * - `provenance.marker.v1.EventMarkerMint`
 * - `provenance.marker.v1.EventMarkerSetDenomMetadata`
 * - `provenance.marker.v1.EventMarkerTransfer`
 * - `provenance.marker.v1.EventMarkerWithdraw`
 */
sealed class EventMarker : FromAttributeMap {

    companion object {
        /**
         * Maps Provenance events like `provenance.marker.v1.EventMarkerMint` to constructors for the `EventMarker`
         * sealed class family.
         */
        val mapper = EventMapper(EventMarker::class)
    }

    /**
     * Convert a marker instance to a consolidated marker event.
     */
    abstract fun toEventRecord(): ConsolidatedEvent

    override fun toString() = toEventRecord().toString()

    /**
     * A container class that is capable of representing the consolidated union of data contained in child classes
     * of [EventMarker].
     */
    abstract class ConsolidatedEvent(
        val coins: String? = null,
        val denom: String? = null,
        val amount: String? = null,
        val administrator: String? = null,
        val toAddress: String? = null,
        val fromAddress: String? = null,
        val metadataBase: String? = null,
        val metadataDescription: String? = null,
        val metadataDisplay: String? = null,
        val metadataDenomUnits: String? = null,
        val metadataName: String? = null,
        val metadataSymbol: String? = null
    ) {
        open fun isActivate(): Boolean = false
        open fun isBurn(): Boolean = false
        open fun isCancel(): Boolean = false
        open fun isDelete(): Boolean = false
        open fun isFinalize(): Boolean = false
        open fun isMint(): Boolean = false
        open fun isSetDenomMetadata(): Boolean = false
        open fun isTransfer(): Boolean = false
        open fun isWithdraw(): Boolean = false
    }

    @MappedProvenanceEvent("provenance.marker.v1.EventMarkerActivate")
    class Activate(override val attributes: Map<String, String?>) : EventMarker() {
        val denom: String? by attributes.transform(::debase64)
        val administrator: String? by attributes.transform(::debase64)

        override fun toEventRecord(): ConsolidatedEvent {
            return object : ConsolidatedEvent(
                denom = denom,
                administrator = administrator
            ) {
                override fun isActivate(): Boolean = true
            }
        }
    }

    @MappedProvenanceEvent("provenance.marker.v1.EventMarkerBurn")
    class Burn(override val attributes: Map<String, String?>) : EventMarker() {
        val amount: String? by attributes.transform(::debase64)
        val denom: String? by attributes.transform(::debase64)
        val administrator: String? by attributes.transform(::debase64)

        override fun toEventRecord(): ConsolidatedEvent = object : ConsolidatedEvent(
            amount = amount,
            denom = denom,
            administrator = administrator
        ) {
            override fun isBurn(): Boolean = true
        }
    }

    @MappedProvenanceEvent("provenance.marker.v1.EventMarkerCancel")
    class Cancel(override val attributes: Map<String, String?>) : EventMarker() {
        val denom: String? by attributes.transform(::debase64)
        val administrator: String? by attributes.transform(::debase64)

        override fun toEventRecord(): ConsolidatedEvent = object : ConsolidatedEvent(
            denom = denom,
            administrator = administrator
        ) {
            override fun isCancel(): Boolean = true
        }
    }

    @MappedProvenanceEvent("provenance.marker.v1.EventMarkerDelete")
    class Delete(override val attributes: Map<String, String?>) : EventMarker() {
        val denom: String? by attributes.transform(::debase64)
        val administrator: String? by attributes.transform(::debase64)

        override fun toEventRecord(): ConsolidatedEvent = object : ConsolidatedEvent(
            denom = denom,
            administrator = administrator
        ) {
            override fun isDelete(): Boolean = true
        }
    }

    @MappedProvenanceEvent("provenance.marker.v1.EventMarkerFinalize")
    class Finalize(override val attributes: Map<String, String?>) : EventMarker() {
        val denom: String? by attributes.transform(::debase64)
        val administrator: String? by attributes.transform(::debase64)

        override fun toEventRecord(): ConsolidatedEvent = object : ConsolidatedEvent(
            denom = denom,
            administrator = administrator
        ) {
            override fun isFinalize(): Boolean = true
        }
    }

    @MappedProvenanceEvent("provenance.marker.v1.EventMarkerMint")
    class Mint(override val attributes: Map<String, String?>) : EventMarker() {
        val amount: String? by attributes.transform(::debase64)
        val denom: String? by attributes.transform(::debase64)
        val administrator: String? by attributes.transform(::debase64)

        override fun toEventRecord(): ConsolidatedEvent = object : ConsolidatedEvent(
            amount = amount,
            denom = denom,
            administrator = administrator
        ) {
            override fun isMint(): Boolean = true
        }
    }

    @MappedProvenanceEvent("provenance.marker.v1.EventMarkerSetDenomMetadata")
    class SetDenomMetadata(override val attributes: Map<String, String?>) : EventMarker() {
        val metadataBase: String? by attributes.transform("metadata_base", ::debase64)
        val metadataDescription: String? by attributes.transform("metadata_description", ::debase64)
        val metadataDisplay: String? by attributes.transform("metadata_display", ::debase64)
        val metadataDenomUnits: String? by attributes.transform("metadata_denom_units", ::debase64)
        val administrator: String? by attributes.transform(::debase64)
        val metadataName: String? by attributes.transform("metadata_name", ::debase64)
        val metadataSymbol: String? by attributes.transform("metadata_symbol", ::debase64)

        override fun toEventRecord(): ConsolidatedEvent = object : ConsolidatedEvent(
            metadataBase = metadataBase,
            metadataDescription = metadataDescription,
            metadataDisplay = metadataDisplay,
            metadataDenomUnits = metadataDenomUnits,
            administrator = administrator,
            metadataName = metadataName,
            metadataSymbol = metadataSymbol
        ) {
            override fun isSetDenomMetadata(): Boolean = true
        }
    }

    @MappedProvenanceEvent("provenance.marker.v1.EventMarkerTransfer")
    class Transfer(override val attributes: Map<String, String?>) : EventMarker() {
        val amount: String? by attributes.transform(::debase64)
        val denom: String? by attributes.transform(::debase64)
        val administrator: String? by attributes.transform(::debase64)
        val toAddress: String? by attributes.transform("to_address", ::debase64)
        val fromAddress: String? by attributes.transform("from_address", ::debase64)

        override fun toEventRecord(): ConsolidatedEvent = object : ConsolidatedEvent(
            amount = amount,
            denom = denom,
            administrator = administrator,
            toAddress = toAddress,
            fromAddress = fromAddress
        ) {
            override fun isTransfer(): Boolean = true
        }
    }

    @MappedProvenanceEvent("provenance.marker.v1.EventMarkerWithdraw")
    class Withdraw(override val attributes: Map<String, String?>) : EventMarker() {
        val coins: String? by attributes.transform(::debase64)
        val denom: String? by attributes.transform(::debase64)
        val administrator: String? by attributes.transform(::debase64)
        val toAddress: String? by attributes.transform("to_address", ::debase64)

        override fun toEventRecord(): ConsolidatedEvent = object : ConsolidatedEvent(
            coins = coins,
            denom = denom,
            administrator = administrator,
            toAddress = toAddress
        ) {
            override fun isWithdraw(): Boolean = true
        }
    }
}
