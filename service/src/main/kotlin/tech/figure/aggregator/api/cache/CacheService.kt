package tech.figure.aggregator.api.cache

import com.google.gson.Gson
import tech.figure.aggregator.api.model.TxCoinTransferData
import tech.figure.aggregator.api.model.TxTotalAmtResponse
import tech.figure.aggregator.api.model.TxFeeData
import tech.figure.aggregator.api.service.AccountService
import tech.figure.aggregator.api.snowflake.SnowflakeJDBC
import tech.figure.aggregate.common.DBConfig
import tech.figure.aggregate.common.logger
import tech.figure.aggregate.repository.database.ravendb.RavenDB
import net.ravendb.client.Constants
import net.ravendb.client.documents.session.IDocumentSession
import net.snowflake.client.jdbc.internal.joda.time.DateTime
import tech.figure.aggregator.api.model.TxDailyTotal
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

    companion object{
        const val DEFAULT_LIMIT = 100
        const val DEFAULT_OFFSET = 1
    }

    fun getNetDateRangeFee(addr: String, startDate: OffsetDateTime, endDate: OffsetDateTime, denom: String): TxTotalAmtResponse {
        val result = getAllTxFee(addr, startDate, endDate)

        return TxTotalAmtResponse(
            addr = addr,
            startDate = startDate.toString(),
            endDate = endDate.toString(),
            total = accountService.calcTotalFees(result, denom),
            denom = denom
        )
    }

    fun getNetDateRangeTx(addr: String, startDate: OffsetDateTime, endDate: OffsetDateTime, denom: String): TxTotalAmtResponse {
        val recordsIn = getAllTxIn(addr, startDate, endDate)
        val recordsOut = getAllTxOut(addr, startDate, endDate)

        return TxTotalAmtResponse(
            addr = addr,
            startDate = startDate.toString(),
            endDate = endDate.toString(),
            total = accountService.calcDailyNetTxns(recordsIn, recordsOut, denom),
            denom = denom
        )
    }

    fun getTxFees(
        address: String,
        startDate: OffsetDateTime,
        endDate: OffsetDateTime,
        limit: Int,
        offset: Int
    ): List<TxFeeData> {
        val session = openSession()

        val cachedFeeRecord = session.query(TxFeeData::class.java)
            .whereEquals("sender", address)
            .whereBetween("blockTimestamp", startDate.format(DateTimeFormatter.ISO_LOCAL_DATE), endDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
            .orderBy("blockTimestamp")
            .skip(offset)
            .take(limit)
            .toList()

        if(cachedFeeRecord.isEmpty()) {
            val feeRecords = loadFeeRequestFromDW(address, startDate, endDate)
            return if(feeRecords.isNotEmpty()) {
                cacheFeeDataSet(session, feeRecords)
                getTxFees(
                    address,
                    startDate,
                    endDate,
                    limit,
                    offset
                )
            } else {
                emptyList()
            }
        }
        return cachedFeeRecord
    }

    fun getTxOutRaw(
        address: String,
        startDate: OffsetDateTime,
        endDate: OffsetDateTime,
        limit: Int,
        offset: Int
    ): List<TxCoinTransferData> {
        val session = openSession()

        val recordOut = session.query(TxCoinTransferData::class.java)
            .whereEquals("sender", address)
            .whereBetween("blockTimestamp", startDate.format(DateTimeFormatter.ISO_LOCAL_DATE), endDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
            .orderBy("blockTimestamp")
            .take(limit)
            .skip(offset)
            .toList()

        if(recordOut.isEmpty()) {
            log.info("transaction data for address = $address is not available within this date range: ($startDate - $endDate)")

            val (_, outResult) = loadTxRequestFromDW(address, startDate, endDate)
            return if(outResult.isNotEmpty()) {
                log.info("caching result data found from snowflake")
                cacheTxCoinTransferDataSet(session, outResult)
                getTxOutRaw(
                    address,
                    startDate,
                    endDate,
                    limit,
                    offset
                )
            } else {
                log.info("no out transaction found for $address at date range $startDate to $endDate")
                emptyList()
            }
        }
        return recordOut
    }

    fun getTxOut(
        address: String,
        startDate: OffsetDateTime,
        endDate: OffsetDateTime,
        limit: Int,
        offset: Int
    ): List<TxDailyTotal> {
        val session = openSession()

        val recordOut = session.query(TxCoinTransferData::class.java)
                .whereEquals("sender", address)
                .whereBetween("blockTimestamp", startDate.format(DateTimeFormatter.ISO_LOCAL_DATE), endDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .orderBy("blockTimestamp")
                .take(limit)
                .skip(offset)
                .toList()

        if(recordOut.isEmpty()) {
            log.info("transaction data for address = $address is not available within this date range: ($startDate - $endDate)")

            val (_, outResult) = loadTxRequestFromDW(address, startDate, endDate)
            return if(outResult.isNotEmpty()) {
                log.info("caching result data found from snowflake")
                cacheTxCoinTransferDataSet(session, outResult)
                getTxOut(
                    address,
                    startDate,
                    endDate,
                    limit,
                    offset
                )
            } else {
                log.info("no out transaction found for $address at date range $startDate to $endDate")
                emptyList()
            }
        }
        return accountService.organizeTxByDate(recordOut, address)
    }

    fun getTxInRaw(
        address: String,
        startDate: OffsetDateTime,
        endDate: OffsetDateTime,
        limit: Int,
        offset: Int
    ): List<TxCoinTransferData> {
        val session = openSession()

        val recordIn = session.query(TxCoinTransferData::class.java)
            .whereEquals("receiver", address)
            .whereBetween("blockTimestamp", startDate.format(DateTimeFormatter.ISO_LOCAL_DATE), endDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
            .orderBy("blockTimestamp")
            .skip(offset)
            .take(limit)
            .toList()

        if(recordIn.isEmpty()) {
            log.info("transaction data for address = $address is not available within this date range: ($startDate - $endDate)")
            val (inResult, _) = loadTxRequestFromDW(address, startDate, endDate)
            return if(inResult.isNotEmpty()) {
                log.info("caching result data found from snowflake")
                cacheTxCoinTransferDataSet(session, inResult)
                getTxInRaw(
                    address,
                    startDate,
                    endDate,
                    limit,
                    offset
                )
            } else {
                log.info("no in transaction found for $address at date range $startDate to $endDate")
                emptyList()
            }
        }
        return recordIn
    }

    fun getTxIn(
        address: String,
        startDate: OffsetDateTime,
        endDate: OffsetDateTime,
        limit: Int,
        offset: Int
    ): List<TxDailyTotal> {
        val session = openSession()

        val recordIn = session.query(TxCoinTransferData::class.java)
                .whereEquals("receiver", address)
                .whereBetween("blockTimestamp", startDate.format(DateTimeFormatter.ISO_LOCAL_DATE), endDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .orderBy("blockTimestamp")
                .skip(offset)
                .take(limit)
                .toList()

        if(recordIn.isEmpty()) {
            log.info("transaction data for address = $address is not available within this date range: ($startDate - $endDate)")
            val (inResult, _) = loadTxRequestFromDW(address, startDate, endDate)
            return if(inResult.isNotEmpty()) {
                log.info("caching result data found from snowflake")
                cacheTxCoinTransferDataSet(session, inResult)
                getTxIn(
                    address,
                    startDate,
                    endDate,
                    limit,
                    offset
                )
            } else {
                log.info("no in transaction found for $address at date range $startDate to $endDate")
                emptyList()
            }
        }
        return accountService.organizeTxByDate(recordIn, address)
    }

    private fun getAllTxFee(
        address: String,
        startDate: OffsetDateTime,
        endDate: OffsetDateTime,
    ): List<TxFeeData> {
        val session = openSession()

        val cachedFeeRecord = session.query(TxFeeData::class.java)
            .whereEquals("sender", address)
            .whereBetween("blockTimestamp", startDate.format(DateTimeFormatter.ISO_LOCAL_DATE), endDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
            .orderBy("blockTimestamp")
            .toList()

        return if(cachedFeeRecord.isEmpty()){
            log.info("fee data for address $address is not available in cache")
            loadFeeRequestFromDW(address, startDate, endDate)
        } else {
            cachedFeeRecord
        }
    }

    private fun getAllTxIn(
        address: String,
        startDate: OffsetDateTime,
        endDate: OffsetDateTime
    ): List<TxCoinTransferData> {
        val session = openSession()

        val recordIn = session.query(TxCoinTransferData::class.java)
            .whereEquals("receiver", address)
            .whereBetween("blockTimestamp", startDate.format(DateTimeFormatter.ISO_LOCAL_DATE), endDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
            .orderBy("blockTimestamp")
            .toList()

        if(recordIn.isEmpty()) {
            log.info("transaction data for address = $address is not available within this date range: ($startDate - $endDate)")
            val (inResult, _) = loadTxRequestFromDW(address, startDate, endDate)
            if(inResult.isNotEmpty()) {
                log.info("caching result data found from snowflake")
                cacheTxCoinTransferDataSet(session, inResult)
            }
            return inResult
        }
        return recordIn
    }

    private fun getAllTxOut(
        address: String,
        startDate: OffsetDateTime,
        endDate: OffsetDateTime
    ): List<TxCoinTransferData> {
        val session = openSession()

        val recordOut = session.query(TxCoinTransferData::class.java)
            .whereEquals("sender", address)
            .whereBetween("blockTimestamp", startDate.format(DateTimeFormatter.ISO_LOCAL_DATE), endDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
            .orderBy("blockTimestamp")
            .toList()

        if(recordOut.isEmpty()) {
            log.info("transaction data for address = $address is not available within this date range: ($startDate - $endDate)")

            val (_, outResult) = loadTxRequestFromDW(address, startDate, endDate)
            if(outResult.isNotEmpty()) {
                log.info("caching result data found from snowflake")
                cacheTxCoinTransferDataSet(session, outResult)
            }
            return outResult
        }
        return recordOut
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
