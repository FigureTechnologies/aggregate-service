package tech.figure.aggregate.common.models.fee

data class Fee(
    val fee: Long? = 0L,
    val denom: String? = "",
    val signerInfo: SignerInfo? = SignerInfo()
)
