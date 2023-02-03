package tech.figure.aggregate.common

data class UploadConfig(
    val extractors: List<String> = emptyList()
) {
    companion object {
        fun empty() = UploadConfig()
    }
}

data class Config (
    val wsNode: String,
    val hrp: String,
    val upload: UploadConfig = UploadConfig.empty(),
    val blockApi: BlockApiConfig,
    val dbConfig: DBConfig,
    val apiHost: String,
    val badBlockRange: List<Long>,
    val msgFeeHeight: Long
)

data class DBConfig(
    val dbHost: String,
    val dbPort: Int,
    val dbUser: String,
    val dbPassword: String,
    val dbName: String,
    val dbSchema: String,
    val cacheUri: String,
    val cacheCheckpoint: String,
    val cacheTable: String,
    val dbMaxConnections: Int
)

data class BlockApiConfig(
    val host: String,
    val port: Int,
    val apiKey: String,
    val maxBlockSize: Int
)
