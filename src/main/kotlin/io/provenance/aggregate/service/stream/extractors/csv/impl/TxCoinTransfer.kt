package io.provenance.aggregate.service.stream.extractors.csv.impl

import io.provenance.aggregate.common.extensions.toISOString
import io.provenance.aggregate.common.models.AmountDenom
import io.provenance.aggregate.common.models.StreamBlock
import io.provenance.aggregate.service.stream.extractors.csv.CSVFileExtractor
import io.provenance.aggregate.service.stream.repository.db.DBInterface
import io.provenance.aggregate.service.stream.models.provenance.cosmos.Tx as CosmosTx

data class CoinTransferDB(
    val event_type: String?,
    val block_height: Long?,
    val block_timestamp: String?,
    val tx_hash: String?,
    val recipient: String?,
    val sender: String?,
    val amount: String?,
    val denom: String?
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
        "tx_hash",
        "recipient",
        "sender",
        "amount",
        "denom"
    )
) {
    override suspend fun extract(block: StreamBlock, dbRepository: DBInterface<Any>) {
        for (event in block.txEvents) {
            CosmosTx.mapper.fromEvent(event)
                ?.let { record: CosmosTx ->
                    when (record) {
                        is CosmosTx.Transfer -> {
                            val amountAndDenom: List<AmountDenom>? = record.amountAndDenom?.let { record.splitAmountAndDenom(it) }
                            amountAndDenom?.map { amountDenom ->
                                val coinTransferData = CoinTransferDB(
                                    event.eventType,
                                    event.blockHeight,
                                    event.blockDateTime?.toISOString(),
                                    event.txHash,
                                    record.recipient,
                                    record.sender,
                                    amountDenom.amount,  // amount
                                    amountDenom.denom  // denom
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
