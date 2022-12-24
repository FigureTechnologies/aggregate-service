package tech.figure.aggregate.common.snowflake

import tech.figure.aggregate.common.logger
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler
import tech.figure.aggregate.common.snowflake.model.TxCoinTransferData
import tech.figure.aggregate.common.snowflake.model.TxFeeData
import java.sql.Connection
import java.sql.DriverManager
import java.time.OffsetDateTime
import java.util.Properties

abstract class SnowflakeJDBC(
    properties: Properties,
    dbUri: String
) {
    private val log = logger()
    protected val conn: Connection = DriverManager.getConnection(dbUri, properties)

    /**
     * Returns the pair of in and out of an address Txns Pair<In, Out>
     */
    fun getNetResult(addr: String, startDate: OffsetDateTime, endDate: OffsetDateTime): Pair<List<TxCoinTransferData>, List<TxCoinTransferData>> =
        Pair(queryTxInResultSet(addr, startDate, endDate), queryTxOutResultSet(addr, startDate, endDate)).also{
            log.info("closing snowflake connection")
            conn.close()
        }

    fun getTotalFee(addr: String, startDate: OffsetDateTime, endDate: OffsetDateTime) =
        queryFee(addr, startDate, endDate).also{
            log.info("closing snowflake connection")
            conn.close()
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

    private fun executeQuery(stmt: String): List<MutableMap<String, Any>> =
        QueryRunner().query(conn, stmt, MapListHandler()).toList()
}

fun List<MutableMap<String, Any>>.toTxCoinTransferData() =
    this.map{ result ->
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

fun List<MutableMap<String, Any>>.toTxFeeData() =
    this.map{ result ->
        TxFeeData(
            result["HASH"].toString(),
            result["EVENT_TYPE"].toString(),
            result["BLOCK_HEIGHT"] as Double,
            result["BLOCK_TIMESTAMP"].toString(),
            result["FEE"].toString(),
            result["FEE_DENOM"].toString(),
            result["SENDER"].toString()
        )
    }
