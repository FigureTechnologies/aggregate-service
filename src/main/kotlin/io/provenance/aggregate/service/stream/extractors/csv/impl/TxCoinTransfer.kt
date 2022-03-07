package io.provenance.aggregate.service.stream.extractors.csv.impl

import io.provenance.aggregate.common.extensions.toISOString
import io.provenance.aggregate.common.models.AmountDenom
import io.provenance.aggregate.common.models.Constants
import io.provenance.aggregate.common.models.StreamBlock
import io.provenance.aggregate.service.stream.extractors.csv.CSVFileExtractor
import io.provenance.aggregate.service.stream.repository.db.DBInterface
import io.provenance.aggregate.service.stream.models.provenance.cosmos.Tx as CosmosTx

data class CoinTransferDB(
    val event_type: String?,
    val block_height: Long?,
    val block_timestamp: String?,
    val recipient: String?,
    val sender: String?,
    val amount: String?,
    val denom: String?,
    val fee: Long?,
    val fee_denom: String? = Constants.FEE_DENOMINATION
)

/**
 * Extract data related to the movement of coins between accounts
 */
class TxCoinTransfer : CSVFileExtractor(
    name = "tx_coin_transfer",
    headers = listOf(
        "hash",
        "event_type",
        "block_height",
        "block_timestamp",
        "recipient",
        "sender",
        "amount",
        "denom",
        "fee",
        "fee_denom"
    )
) {
    /**
     * Given a string like `12275197065nhash`, where the amount and denomination are concatenated, split the string
     * into separate amount and denomination strings.
     *
     * To determine amount, consume as many numeric values from the string until a non-numeric value is encountered.
     */
    private fun splitAmountAndDenom(str: String): List<AmountDenom> {

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

    override suspend fun extract(block: StreamBlock, dbRepository: DBInterface<Any>) {
        for (event in block.txEvents) {
            CosmosTx.mapper.fromEvent(event)
                ?.let { record: CosmosTx ->
                    when (record) {
                        is CosmosTx.Transfer -> {
                            val amountAndDenom: List<AmountDenom>? =
                                record.amountAndDenom?.let { splitAmountAndDenom(it) }
                            amountAndDenom?.map { amountDenom ->

                                val coinTransferData = CoinTransferDB(
                                    event.eventType,
                                    event.blockHeight,
                                    event.blockDateTime?.toISOString(),
                                    record.recipient,
                                    record.sender,
                                    amountDenom.amount,  // amount
                                    amountDenom.denom,  // denom
                                    event.fee,
                                    event.feeDenom,
                                )

                                syncWriteRecord(
                                    coinTransferData,
                                    includeHash = true
                                ).also { hash ->
                                    dbRepository.save(hash = hash, coinTransferData)
                                }
                            }
                        }
                    }
                }
        }

    }
}
