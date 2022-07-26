package com.provenance.aggregator.api.snowflake

import com.provenance.aggregator.api.model.TxCoinTransferData
import io.provenance.aggregate.common.logger
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler
import java.sql.DriverManager
import java.time.OffsetDateTime
import java.util.Properties

class SnowflakeJDBC(
    properties: Properties,
    dbUri: String
) {
    private val log = logger()
    private val conn = DriverManager.getConnection(dbUri, properties)

    /**
     * Returns the pair of in and out of an address Txns Pair<In, Out>
     */
    fun getNetResult(addr: String, dateTime: OffsetDateTime): Pair<List<TxCoinTransferData>, List<TxCoinTransferData>> =
        Pair(queryInResultSet(addr, dateTime), queryOutResultSet(addr, dateTime)).also{
            log.info("closing connection")
            conn.close()
        }

    private fun queryOutResultSet(addr: String, currOffSetDate: OffsetDateTime): List<TxCoinTransferData> {
        val querySendStmt = "SELECT * FROM COIN_TRANSFER " +
                "WHERE BLOCK_TIMESTAMP BETWEEN '$currOffSetDate' and '${currOffSetDate.plusDays(1)}' " +
                "AND SENDER='$addr';"

        return executeQuery(querySendStmt).also{ log.info("out result size: ${it.size}") }
    }

    private fun queryInResultSet(addr: String, currOffSetDate: OffsetDateTime): List<TxCoinTransferData> {
        val queryReceiveStmt = "SELECT * FROM COIN_TRANSFER " +
                "WHERE BLOCK_TIMESTAMP BETWEEN '$currOffSetDate' and '${currOffSetDate.plusDays(1)}' " +
                "AND RECIPIENT='$addr';"

        return executeQuery(queryReceiveStmt).also{ log.info("in result size: ${it.size}") }
    }

    private fun executeQuery(stmt: String): List<TxCoinTransferData> {
        val resultData = QueryRunner().query(conn, stmt, MapListHandler()).toList()

        log.info("Snowflake queried ${resultData.size} results")

        return resultData.map { result ->
            TxCoinTransferData(
                result["HASH"].toString(),
                result["EVENT_TYPE"].toString(),
                result["BLOCK_HEIGHT"] as Double,
                result["BLOCK_TIMESTAMP"].toString(),
                result["TX_HASH"].toString(),
                result["RECIPIENT"].toString(),
                result["SENDER"].toString(),
                result["AMOUNT"].toString(),
                result["DENOM"].toString()
            )
        }
    }
}
