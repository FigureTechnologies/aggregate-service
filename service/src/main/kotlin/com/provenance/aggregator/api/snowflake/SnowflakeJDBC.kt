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

    fun executeQuery(addr: String, currOffSetDate: OffsetDateTime): List<TxCoinTransferData> {

        val nextDate = currOffSetDate.plusDays(1)

        val queryStmt = "SELECT * FROM COIN_TRANSFER " +
                "WHERE BLOCK_TIMESTAMP BETWEEN '$currOffSetDate' and '$nextDate' " +
                "AND SENDER='$addr';"

        val resultData = QueryRunner().query(conn, queryStmt, MapListHandler()).toList()

        log.info("Snowflake queried ${resultData.size} results - closing connection").also {
            conn.close()
        }

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
