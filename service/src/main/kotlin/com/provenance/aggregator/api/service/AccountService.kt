package com.provenance.aggregator.api.service

import com.provenance.aggregator.api.model.TxCoinTransferData

class AccountService{

    fun calcDailyNetTxns(inResult: List<TxCoinTransferData>, outResult: List<TxCoinTransferData>): Long =
        calcTotalAmt(inResult) - calcTotalAmt(outResult)


    private fun calcTotalAmt(result: List<TxCoinTransferData>): Long {
        var totalAmt: Long = 0;

        result.map {
            totalAmt = if(totalAmt.toInt() == 0) {
                it.amount.toLong()
            } else {
                totalAmt + it.amount.toLong()
            }
        }

        return totalAmt
    }
}
