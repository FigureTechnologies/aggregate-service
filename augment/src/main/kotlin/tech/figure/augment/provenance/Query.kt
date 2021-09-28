package tech.figure.augment.provenance

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import tech.figure.augment.dsl.Data
import tech.figure.augment.dsl.Module.BANK
import tech.figure.augment.dsl.Row
import tech.figure.augment.dsl.RpcFilter
import tech.figure.augment.dsl.RpcSource

fun findField(field: String, row: Row, rpcFilter: RpcFilter?): String {
    val value = row[field] ?: rpcFilter?.takeIf { it.setter == field }?.value

    return value ?: throw IllegalStateException("\"$field\" was not found in row or rpcFilter")
}

suspend fun query(provenanceClient: ProvenanceClient, rpcSource: RpcSource, data: Data): Data = coroutineScope {
    when (rpcSource.module) {
        BANK -> {
            data.map { row ->
                async {
                    val address = findField("address", row, rpcSource.filter)
                    val denom = findField("denom", row, rpcSource.filter)
                    val blockHeight = findField("block_height", row, rpcSource.filter)

                    row + mapOf("balance" to provenanceClient.denomBalance(address, denom, blockHeight).balance.amount)
                }
            }.awaitAll()
        }
    }
}
