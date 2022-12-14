package tech.figure.aggregate.common

import tech.figure.aggregate.common.aws.s3.S3Bucket

data class S3Config(
    val bucket: S3Bucket
)

data class AwsConfig(
    val region: String?,
    val s3: S3Config
)

data class UploadConfig(
    val extractors: List<String> = emptyList()
) {
    companion object {
        fun empty() = UploadConfig()
    }
}

data class Config (
    val aws: AwsConfig,
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
    val addr: String,
    val dbName: String,
    val cacheTable: String,
    val dbMaxConnections: Int
)

data class BlockApiConfig(
    val host: String,
    val port: Int,
    val apiKey: String
)
