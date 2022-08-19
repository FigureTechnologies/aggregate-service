package com.tech.figure.aggregator.api.model

data class TxFeeData(
    val hash: String,
    val txHash: String,
    val blockHeight: Double,
    val blockTimestamp: String,
    val fee: String,
    val feeDenom: String,
    val sender: String
)
