package com.provenance.aggregator.api.service

import com.provenance.aggregator.api.model.TxCoinTransferData

class AccountService{

    fun calcDailyNetTxns(inResult: List<TxCoinTransferData>, outResult: List<TxCoinTransferData>, denom: String) =
        calcTotalAmt(inResult, denom) - calcTotalAmt(outResult, denom)

    private fun calcTotalAmt(result: List<TxCoinTransferData>, denom: String): Long {
        var totalAmt: Long = 0;

        result.map {
            if(it.denom == denom) {
                totalAmt = if (totalAmt.toInt() == 0) {
                    it.amount.toLong()
                } else {
                    totalAmt + it.amount.toLong()
                }
            }
        }

        return totalAmt
    }
}
