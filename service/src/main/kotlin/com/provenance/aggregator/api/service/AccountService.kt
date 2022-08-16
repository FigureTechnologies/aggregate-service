package com.provenance.aggregator.api.service

import com.provenance.aggregator.api.model.TxCoinTransferData
import com.provenance.aggregator.api.model.TxFeeData

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
