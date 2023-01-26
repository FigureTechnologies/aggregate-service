package tech.figure.aggregate.common.db

import org.apache.commons.dbutils.handlers.MapListHandler
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import tech.figure.aggregate.common.db.model.TxCoinTransferData
import tech.figure.aggregate.common.db.model.TxFeeData
import tech.figure.aggregate.common.logger
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
            result["BLOCK_TIMESTAMP"].toString(),
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
