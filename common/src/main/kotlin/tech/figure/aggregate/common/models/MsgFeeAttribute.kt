package tech.figure.aggregate.common.models

data class MsgFeeAttribute(
    val msgType: String,
    val count: Int,
    val total: String,
    val recipient: String
)
