package tech.figure.aggregate.common.db

import tech.figure.aggregate.proto.TransferServiceOuterClass.StreamRequest
import tech.figure.aggregate.proto.TransferServiceOuterClass.StreamRequest.DenomRequestTypeCase.ALL_DENOM_REQUEST
import tech.figure.aggregate.proto.TransferServiceOuterClass.StreamRequest.DenomRequestTypeCase.DENOMREQUESTTYPE_NOT_SET
import tech.figure.aggregate.proto.TransferServiceOuterClass.StreamRequest.DenomRequestTypeCase.FILTERED_DENOM_REQUEST
import tech.figure.aggregate.proto.TransferServiceOuterClass.StreamType
import tech.figure.aggregate.proto.TransferServiceOuterClass.StreamType.COIN_TRANSFER
import tech.figure.aggregate.proto.TransferServiceOuterClass.StreamType.MARKER_SUPPLY
import tech.figure.aggregate.proto.TransferServiceOuterClass.StreamType.MARKER_TRANSFER
import org.apache.commons.dbutils.handlers.MapListHandler
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import tech.figure.aggregate.common.db.model.TxCoinTransferData
import tech.figure.aggregate.common.db.model.TxFeeData
import tech.figure.aggregate.common.db.model.TxMarkerSupply
import tech.figure.aggregate.common.db.model.TxMarkerTransfer
import tech.figure.aggregate.common.db.model.impl.TxResponseData
import tech.figure.aggregate.common.logger
import java.sql.Timestamp
import java.time.OffsetDateTime

abstract class DBJdbc {

    private val log = logger()

    /**
     * Returns the pair of in and out of an address Txns Pair<In, Out>
     */
    fun getNetResult(addr: String, startDate: OffsetDateTime, endDate: OffsetDateTime): Pair<List<TxCoinTransferData>, List<TxCoinTransferData>> =
        Pair(queryTxInResultSet(addr, startDate, endDate), queryTxOutResultSet(addr, startDate, endDate))

    fun getTotalFee(addr: String, startDate: OffsetDateTime, endDate: OffsetDateTime) =
        queryFee(addr, startDate, endDate)

    fun streamTransferHistorical(streamRequest: StreamRequest): List<TxResponseData> =
        when(streamRequest.denomRequestTypeCase) {
            ALL_DENOM_REQUEST -> queryAllHistoricalTxData(
                streamRequest.allDenomRequest.blockHeight,
                streamRequest.streamType
            )
            FILTERED_DENOM_REQUEST -> queryFilteredDenomHistoricalTxData(
                streamRequest.filteredDenomRequest.blockHeight,
                streamRequest.filteredDenomRequest.denomList,
                streamRequest.streamType
            )
            DENOMREQUESTTYPE_NOT_SET -> error("No denom request type was set.")
        }

    private fun queryAllHistoricalTxData(blockHeight: Long, type: StreamType): List<TxResponseData> {
        val stmt = "SELECT * FROM $type WHERE BLOCK_HEIGHT >= $blockHeight;"
        executeQuery(stmt).also {
            return when(type) {
                COIN_TRANSFER -> it.toTxCoinTransferData()
                MARKER_TRANSFER -> it.toTxMarkerTransfer()
                MARKER_SUPPLY -> it.toTxMarkerSupply()
                else -> error("Unknown stream type requested.")
            }
        }
    }

    private fun queryFilteredDenomHistoricalTxData(
        blockHeight: Long, denomList: List<String>,
        type: StreamType
    ): List<TxResponseData> {
        val stmt = "SELECT * FROM $type WHERE BLOCK_HEIGHT >= $blockHeight AND "
        val denomStmt = denomList.joinToString(separator = " OR") { " DENOM = \'$it\'" }
        executeQuery("$stmt$denomStmt;").also {
            return when(type) {
                COIN_TRANSFER -> it.toTxCoinTransferData()
                MARKER_TRANSFER -> it.toTxMarkerTransfer()
                MARKER_SUPPLY -> it.toTxMarkerSupply()
                else -> error("Unknown stream type requested.")
            }
        }
    }

    private fun queryFee(addr: String, startDate: OffsetDateTime, endDate: OffsetDateTime): List<TxFeeData> {
        val queryFeeStmt = "SELECT * FROM FEES " +
                "WHERE BLOCK_TIMESTAMP BETWEEN '$startDate' and '$endDate' " +
                "AND SENDER='$addr';"

        return executeQuery(queryFeeStmt).toTxFeeData()
            .also { log.info("out result size: ${it.size}") }
    }

    private fun queryTxOutResultSet(addr: String, startDate: OffsetDateTime, endDate: OffsetDateTime): List<TxCoinTransferData> {
        val querySendStmt = "SELECT * FROM COIN_TRANSFER " +
                "WHERE BLOCK_TIMESTAMP BETWEEN '$startDate' and '$endDate' " +
                "AND SENDER='$addr'" +
                "ORDER BY BLOCK_TIMESTAMP";

        return executeQuery(querySendStmt).toTxCoinTransferData()
            .also{ log.info("out result size: ${it.size}") }
    }

    private fun queryTxInResultSet(addr: String, startDate: OffsetDateTime, endDate: OffsetDateTime): List<TxCoinTransferData> {
        val queryReceiveStmt = "SELECT * FROM COIN_TRANSFER " +
                "WHERE BLOCK_TIMESTAMP BETWEEN '$startDate' and '$endDate' " +
                "AND RECIPIENT='$addr'" +
                "ORDER BY BLOCK_TIMESTAMP";

        return executeQuery(queryReceiveStmt).toTxCoinTransferData()
            .also{ log.info("in result size: ${it.size}") }
    }

    private fun executeQuery(stmt: String): List<MutableMap<String, Any>> {
        val result = transaction {
                TransactionManager.current().connection
                    .prepareStatement(stmt, false)
                    .executeQuery()
            }
        return MapListHandler().handle(result).toList()
    }
}

fun List<MutableMap<String, Any>>.toTxCoinTransferData() =
    this.map{ result ->
        TxCoinTransferData(
            result["HASH"].toString(),
            result["EVENT_TYPE"].toString(),
            result["BLOCK_HEIGHT"] as Long,
            result["BLOCK_TIMESTAMP"] as Timestamp,
            result["TX_HASH"].toString(),
            result["RECIPIENT"].toString(),
            result["SENDER"].toString(),
            result["AMOUNT"].toString(),
            result["DENOM"].toString()
        )
    }

fun List<MutableMap<String, Any>>.toTxFeeData() =
    this.map{ result ->
        TxFeeData(
            result["HASH"].toString(),
            result["EVENT_TYPE"].toString(),
            result["BLOCK_HEIGHT"] as Long,
            result["BLOCK_TIMESTAMP"].toString(),
            result["FEE"].toString(),
            result["FEE_DENOM"].toString(),
            result["SENDER"].toString()
        )
    }

fun List<MutableMap<String, Any>>.toTxMarkerSupply() =
    this.map { result ->
        TxMarkerSupply(
            result["HASH"].toString(),
            result["EVENT_TYPE"].toString(),
            result["BLOCK_HEIGHT"] as Long,
            result["BLOCK_TIMESTAMP"] as Timestamp,
            result["COINS"].toString(),
            result["DENOM"].toString(),
            result["AMOUNT"].toString(),
            result["ADMINISTRATOR"].toString(),
            result["TO_ADDRESS"].toString(),
            result["FROM_ADDRESS"].toString(),
            result["METADATA_BASE"].toString(),
            result["METADATA_DESCRIPTION"].toString(),
            result["METADATA_DISPLAY"].toString(),
            result["METADATA_DENOM_UNITS"].toString(),
            result["METADATA_NAME"].toString(),
            result["METADATA_SYMBOL"].toString()
        )
    }

fun List<MutableMap<String, Any>>.toTxMarkerTransfer() =
    this.map { result ->
        TxMarkerTransfer(
            result["HASH"].toString(),
            result["EVENT_TYPE"].toString(),
            result["BLOCK_HEIGHT"] as Long,
            result["BLOCK_TIMESTAMP"]as Timestamp,
            result["AMOUNT"].toString(),
            result["DENOM"].toString(),
            result["ADMINISTRATOR"].toString(),
            result["TO_ADDRESS"].toString(),
            result["FROM_ADDRESS"].toString()
        )

    }
