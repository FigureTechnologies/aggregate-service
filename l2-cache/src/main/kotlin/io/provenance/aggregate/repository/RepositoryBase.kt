package io.provenance.aggregate.repository

import io.provenance.aggregate.common.models.BlockResultsResponseResultTxsResults
import io.provenance.aggregate.common.models.StreamBlock
import io.provenance.aggregate.common.models.TxEvent

interface RepositoryBase<T> {

    fun saveBlockMetadata(block: StreamBlock)

    fun saveBlockTx(blockHeight: Long?, blockTxResult: List<BlockResultsResponseResultTxsResults>?, txHash: (Int) -> String?)

    fun saveBlockTxEvents(blockHeight: Long?, txEvents: List<TxEvent>?)

    fun saveChanges()
}
