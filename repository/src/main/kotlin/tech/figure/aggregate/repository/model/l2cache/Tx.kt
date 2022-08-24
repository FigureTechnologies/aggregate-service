package tech.figure.aggregate.repository.model.l2cache

data class Tx(
    val txHash: String?,
    val blockHeight: Long?,
    val code: Long?,
    val data: String?,
    val log: String?,
    val info: String?,
    val gasWanted: Long?,
    val gasUsed: Long?,
    val numEvents: Long?
)
