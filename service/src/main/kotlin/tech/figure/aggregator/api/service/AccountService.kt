package tech.figure.aggregator.api.service

import tech.figure.aggregate.common.db.model.TxCoinTransferData
import tech.figure.aggregator.api.model.TxDailyTotal
import tech.figure.aggregate.common.db.model.TxFeeData
import java.time.ZoneOffset

class AccountService{

    fun calcDailyNetTxns(inResult: List<TxCoinTransferData>, outResult: List<TxCoinTransferData>, denom: String): Long {
        val txAmtList = mutableListOf<Long>()

        inResult.map {
            if(it.denom == denom) {
                txAmtList.add(it.amount.toLong())
            }
        }

        val inTotalAmt = calcTotalAmt(txAmtList).also {
            txAmtList.clear()
        }

        outResult.map {
            if(it.denom == denom) {
                txAmtList.add(it.amount.toLong())
            }
        }

        val outTotalAmt = calcTotalAmt(txAmtList)

        return inTotalAmt - outTotalAmt
    }

    fun calcTotalFees(result: List<TxFeeData>, denom: String): Long {
        val feeAmtList = mutableListOf<Long>()
        result.map {
            if(it.feeDenom == denom) {
                feeAmtList.add(it.fee.toLong())
            }
        }
        return calcTotalAmt(feeAmtList)
    }

    fun organizeTxByDate(txCoinTransferDataList: List<TxCoinTransferData>, address: String): List<TxDailyTotal> {

        // we only care for the 1st 10 characters of the date ex. 2022-01-01
        val distinctByDate = txCoinTransferDataList.distinctBy {
            it.blockTimestamp.toInstant().atOffset(ZoneOffset.UTC).toString().take(10)
        }
        val uniqueDates = distinctByDate.map { it.blockTimestamp }.toList()

        return uniqueDates.map { date ->
            val txAmtByDate = txCoinTransferDataList.filter {
                it.blockTimestamp.toInstant().atOffset(ZoneOffset.UTC).toString().take(10) ==
                        date.toInstant().atOffset(ZoneOffset.UTC).toString().take(10)
            }.map {
                //todo: fix this when we start allowing for different denom type.
                if(it.denom == "nhash") {
                    it.amount.toLong()
                } else {
                    0
                }
            }

            TxDailyTotal(
                address = address,
                date = date.toInstant().atOffset(ZoneOffset.UTC).toString().take(10),
                total = calcTotalAmt(txAmtByDate),
                denom = "nhash"
            )
        }.toList()

        return emptyList()
    }

    private fun calcTotalAmt(amount: List<Long>): Long {
        var totalAmt: Long = 0
        amount.map {
            totalAmt = if(totalAmt.toInt() == 0) {
                it
            } else {
                totalAmt + it
            }
        }
        return totalAmt
    }
}
