package tech.figure.aggregate.service.stream.extractors.csv.impl

import tech.figure.aggregate.common.toISOString
import tech.figure.aggregate.common.models.AmountDenom
import tech.figure.aggregate.common.models.block.StreamBlock
import tech.figure.aggregate.common.models.stream.CoinTransfer
import tech.figure.aggregate.service.stream.extractors.csv.CSVFileExtractor
import tech.figure.aggregate.service.stream.kafka.BaseKafkaProducerParam.CoinTransferParam
import tech.figure.aggregate.service.stream.kafka.KafkaProducerFactory
import tech.figure.aggregate.service.stream.models.cosmos.Tx as CosmosTx

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

    override suspend fun extract(block: StreamBlock, producer: KafkaProducerFactory?) {
        for(txData in block.blockTxData) {
            for(event in txData.events) {
                tech.figure.aggregate.service.stream.models.cosmos.Tx.mapper.fromEvent(event)
                    ?.let { record: CosmosTx ->
                        when(record) {
                            is CosmosTx.Transfer -> {
                                val amountAndDenom: List<AmountDenom>? = record.amountAndDenom?.let { record.splitAmountAndDenom(it)}
                                amountAndDenom?.map { amountDenom ->
                                    val coinTransferData = CoinTransfer(
                                        event.eventType,
                                        event.blockHeight,
                                        event.blockDateTime?.toISOString(),
                                        event.txHash,
                                        record.recipient,
                                        record.sender,
                                        amountDenom.amount,
                                        amountDenom.denom
                                    )

                                    syncWriteRecord(
                                        coinTransferData.eventType,
                                        coinTransferData.blockHeight,
                                        coinTransferData.blockTimestamp,
                                        coinTransferData.txHash,
                                        coinTransferData.recipient,
                                        coinTransferData.sender,
                                        coinTransferData.amount,
                                        coinTransferData.denom
                                    )

                                    producer?.publish(CoinTransferParam(coinTransferData))
                                }
                            }
                        }
                    }
            }
        }
    }
}
