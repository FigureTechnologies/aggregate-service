package tech.figure.aggregator.api.cache

import com.google.gson.Gson
import tech.figure.aggregator.api.model.response.TxResponse
import tech.figure.aggregator.api.model.TxCoinTransferData
import tech.figure.aggregator.api.model.TxTotalAmtResponse
import tech.figure.aggregator.api.model.TxFeeData
import tech.figure.aggregator.api.service.AccountService
import tech.figure.aggregator.api.snowflake.SnowflakeJDBC
import io.ktor.http.HttpStatusCode
import tech.figure.aggregate.common.DBConfig
import tech.figure.aggregate.common.logger
import tech.figure.aggregate.repository.database.ravendb.RavenDB
import net.ravendb.client.Constants
import net.ravendb.client.documents.session.IDocumentSession
import net.snowflake.client.jdbc.internal.joda.time.DateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

class CacheService(
    private val properties: Properties,
    private val dwUri: String,
    private val config: DBConfig
): RavenDB(
    config.addr,
    config.cacheTable,
    config.dbMaxConnections
) {

    private val log = logger()
    private val accountService = AccountService()

    fun getFee(addr: String, startDate: OffsetDateTime, endDate: OffsetDateTime, denom: String): TxResponse {
        val session = openSession()

        val cachedFeeRecord = session.query(TxFeeData::class.java)
            .whereEquals("sender", addr)
            .whereBetween("blockTimestamp", startDate.format(DateTimeFormatter.ISO_LOCAL_DATE), endDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
            .toList()

        if(cachedFeeRecord.isEmpty()) {
            log.info("fee data for address $addr is not available in cache")

            val result = loadFeeRequestFromDW(addr, startDate, endDate)
            if(result.isNotEmpty()) {
                log.info("caching fee data results sets from snowflake")
                cacheFeeDataSet(session, result)

                val totalFees = accountService.calcTotalFees(result, denom)
                return TxResponse(
                    TxTotalAmtResponse(addr, startDate.toString(), endDate.toString(), totalFees, denom).json(),
                    HttpStatusCode.OK
                ).also{ log.info(it.toString()) }
            } else {
                log.info("no transaction found from snowflake")
                return TxResponse(
                    "$addr fee for data range $startDate to $endDate not found".json(),
                    HttpStatusCode.NotFound
                )
            }
        } else {
            log.info("Found data from ravendb: fee=${cachedFeeRecord.size}")
            return TxResponse(
                TxTotalAmtResponse(addr, startDate.toString(), endDate.toString(), accountService.calcTotalFees(cachedFeeRecord, denom), denom).toString(),
                HttpStatusCode.OK
            ).also {
                log.info(it.toString())
            }
        }
    }

    fun getTx(addr: String, startDate: OffsetDateTime, endDate: OffsetDateTime, denom: String): TxResponse {
        val session = openSession()

        val recordIn = session.query(TxCoinTransferData::class.java)
            .whereEquals("receiver", addr)
            .whereBetween("blockTimestamp", startDate.format(DateTimeFormatter.ISO_LOCAL_DATE), endDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
            .toList()

        val recordOut = session.query(TxCoinTransferData::class.java)
            .whereEquals("sender", addr)
            .whereBetween("blockTimestamp", startDate.format(DateTimeFormatter.ISO_LOCAL_DATE), endDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
            .toList()

        if(recordIn.isEmpty() && recordOut.isEmpty()) {
            //No previous data found, retrieve then cache into raven.
            log.info("transaction data for address $addr is not available in cache")

            val (inResult, outResult) = loadTxRequestFromDW(addr, startDate, endDate)
            if(inResult.isNotEmpty() || outResult.isNotEmpty()) {
                log.info("Caching data result sets from Snowflake")

                // cache the data retrieved
                cacheTxCoinTransferDataSet(session, inResult)
                cacheTxCoinTransferDataSet(session, outResult)

                // calc the daily transaction in hash
                val calcResults = accountService.calcDailyNetTxns(inResult, outResult, denom)
                return TxResponse(
                    TxTotalAmtResponse(addr, startDate.toString(), endDate.toString(), calcResults, denom).toString(),
                    HttpStatusCode.OK
                ).also { log.info(it.toString()) }
            } else {
                log.info("no transaction found from snowflake")
                return TxResponse(
                    "$addr data for date range $startDate to $endDate not found".json(),
                    HttpStatusCode.NotFound
                ).also { log.info(it.toString()) }
            }
        }else {
            log.info("Found data from ravendb: in=${recordIn.size} out=${recordOut.size}")
            return TxResponse(
                TxTotalAmtResponse(addr, startDate.toString(), endDate.toString(), accountService.calcDailyNetTxns(recordIn, recordOut, denom), denom).json(),
                HttpStatusCode.OK
            ).also {
                log.info(it.toString())
            }
        }
    }

    /**
     * Reauthenticate with Snowflake every time we query to Snowflake
     */
    private fun loadTxRequestFromDW(address: String, startDate: OffsetDateTime, endDate: OffsetDateTime) =
        SnowflakeJDBC(properties, dwUri).getNetResult(address, startDate, endDate)

    private fun loadFeeRequestFromDW(address: String, startDate: OffsetDateTime, endDate: OffsetDateTime) =
        SnowflakeJDBC(properties, dwUri).getTotalFee(address, startDate, endDate)

    private fun configureEviction(data: Any, session: IDocumentSession) {
        //Evict data after 30 days
        val expiry = DateTime.now().plusDays(30)
        session.advanced().getMetadataFor(data)[Constants.Documents.Metadata.EXPIRES] = expiry
    }

    private fun cacheTxCoinTransferDataSet(session: IDocumentSession, result: List<TxCoinTransferData>) {
        result.map { txCoinTransferData ->
            session.store(txCoinTransferData, txCoinTransferData.hash)
            //Set eviction after 30 days.
            configureEviction(txCoinTransferData, session)
        }
        session.saveChanges()
    }

    private fun cacheFeeDataSet(session: IDocumentSession, result: List<TxFeeData>) {
        result.map { txFeeData ->
            session.store(txFeeData, txFeeData.hash)
            //Set eviction after 30 days.
            configureEviction(txFeeData, session)
        }
        session.saveChanges()
    }
}

fun Any.json(): String = Gson().toJson(this)
