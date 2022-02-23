package io.provenance.aggregate.service.stream.models.provenance.cosmos

import io.provenance.aggregate.common.extensions.transform
import io.provenance.aggregate.common.models.AmountDenom
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
        val recipient: String? by attributes.transform(::debase64)
        val sender: String? by attributes.transform(::debase64)
        val amountAndDenom: String? by attributes.transform("amount", ::debase64)

        fun isFeeCollector(feeCollector: String) = recipient == feeCollector

        /**
         * Given a string like `12275197065nhash`, where the amount and denomination are concatenated, split the string
         * into separate amount and denomination strings.
         *
         * To determine amount, consume as many numeric values from the string until a non-numeric value is encountered.
         */
        fun splitAmountAndDenom(str: String): List<AmountDenom> {

            var amountDenomList = mutableListOf<AmountDenom>()

            /**
             * There has been instances where amounts have been concatenated together in a single row
             *
             *  ex. "53126cfigurepayomni,100nhash"
             *
             *  Accounting has requested that we separate this into 2 rows.
             *
             */
            str.split(",").map {
                val amount = StringBuilder(it)
                val denom = StringBuilder()
                for (i in it.length - 1 downTo 0) {
                    val ch = it[i]
                    if (!ch.isDigit()) {
                        amount.deleteCharAt(i)
                        denom.insert(0, ch)
                    } else {
                        break
                    }
                }
                amountDenomList.add(AmountDenom(amount.toString(), denom.toString()))
            }

            return amountDenomList
        }
    }
}
