package tech.figure.aggregate.common.models.fee

data class SignerInfo(
    val signerAddrs: List<String>? = mutableListOf(),
    val incurrAddr: String? = ""
)
