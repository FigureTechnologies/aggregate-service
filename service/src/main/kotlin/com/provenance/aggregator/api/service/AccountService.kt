package com.provenance.aggregator.api.service

import com.provenance.aggregator.api.com.provenance.aggregator.api.model.TxCoinTransferData

class AccountService{

    fun calcDailyTransactionHash(txList: List<TxCoinTransferData>): Long {
        var totalHashAmt: Long = 0;
        txList.map {
            totalHashAmt = if(totalHashAmt.toInt() == 0) {
                it.amount.toLong()
            } else {
                totalHashAmt + it.amount.toLong()
            }
        }
        return totalHashAmt
    }
}
