package com.provenance.aggregator.api.cache

import com.google.gson.Gson
import com.provenance.aggregator.api.model.Response
import com.provenance.aggregator.api.model.TxCoinTransferData
import com.provenance.aggregator.api.model.TxDailyTotal
import com.provenance.aggregator.api.service.AccountService
import com.provenance.aggregator.api.snowflake.SnowflakeJDBC
import io.ktor.http.HttpStatusCode
import io.provenance.aggregate.common.DBConfig
import io.provenance.aggregate.common.logger
import io.provenance.aggregate.repository.database.ravendb.RavenDB
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

    fun getTx(addr: String, date: OffsetDateTime): Response {
        val session = openSession()
        val record = session.query(TxCoinTransferData::class.java)
            .whereEquals("sender", addr)
            .whereBetween("blockTimestamp", date.format(DateTimeFormatter.ISO_LOCAL_DATE), date.plusDays(1).format(
                DateTimeFormatter.ISO_LOCAL_DATE))
            .toList()

        if(record.isEmpty()) {
            //No previous data found, retrieve then cache into raven.
            log.debug("transaction data for address $addr is not available in cache")

            val addrTxs: List<TxCoinTransferData> = loadFromDataWarehouse(addr, date)
            if(addrTxs.isNotEmpty()) {
                log.debug("snowflake results found: ${addrTxs.size}")

                // cache the data retrieved
                addrTxs.map { txCoinTransfer ->
                    session.store(txCoinTransfer, txCoinTransfer.hash)
                    //Set eviction after 7 days.
                    configureEviction(txCoinTransfer, session)
                }

                saveChanges(session)

                // calc the daily transaction in hash
                val calcResults = accountService.calcDailyTransactionHash(addrTxs)
                return Response(
                    TxDailyTotal(addr, date.toString(), calcResults).json(),
                    HttpStatusCode.OK
                ).also { log.info(it.toString()) }
            } else {
                log.info("no transaction found from snowflake")
                return Response(
                    "$addr with date $date not found",
                    HttpStatusCode.NotFound
                ).also { log.info(it.toString()) }
            }
        }else {
            log.info("Found data from ravendb: ${record.size}")
            return Response(
                TxDailyTotal(addr, date.toString(), accountService.calcDailyTransactionHash(record)).json(),
                HttpStatusCode.OK
            ).also {
                log.info(it.toString())
            }
        }
    }

    /**
     * Reauthenticate with Snowflake every time we query to Snowflake
     */
    private fun loadFromDataWarehouse(address: String, queryDate: OffsetDateTime) =
        SnowflakeJDBC(properties, dwUri).executeQuery(address, queryDate)

    private fun configureEviction(txData: TxCoinTransferData, session: IDocumentSession) {
        //Evict data after 30 days
        val expiry = DateTime.now().plusDays(30)
        session.advanced().getMetadataFor(txData)[Constants.Documents.Metadata.EXPIRES] = expiry
    }
}

fun Any.json(): String = Gson().toJson(this)
