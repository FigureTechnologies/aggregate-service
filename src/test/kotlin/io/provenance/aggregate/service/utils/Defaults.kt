package io.provenance.aggregate.service.test.utils

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.provenance.aggregate.common.DynamoConfig
import io.provenance.aggregate.common.DynamoTable
import io.provenance.aggregate.common.S3Config
import io.provenance.aggregate.service.adapter.json.JSONObjectAdapter
import io.provenance.aggregate.common.aws.s3.S3Bucket
import io.provenance.aggregate.common.models.BlockResponse
import io.provenance.aggregate.common.models.BlockResultsResponse
import io.provenance.aggregate.common.models.BlockchainResponse
import io.provenance.eventstream.decoder.moshiDecoderAdapter
import io.provenance.eventstream.net.NetAdapter
import io.provenance.eventstream.net.okHttpNetAdapter

object Defaults {

    val moshi: Moshi = newMoshi()

    fun newMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .add(JSONObjectAdapter())
        .build()

    val templates = newTemplate()

    fun newTemplate(): Template = Template(moshi)

    fun blockResponses(): Array<BlockResponse> =
        heights
            .map { templates.unsafeReadAs(BlockResponse::class.java, "block/${it}.json") }
            .toTypedArray()

    fun blockResultsResponses(): Array<BlockResultsResponse> =
        heights
            .map { templates.unsafeReadAs(BlockResultsResponse::class.java, "block_results/${it}.json") }
            .toTypedArray()

    fun blockchainResponses(): Array<BlockchainResponse> =
        heightChunks
            .map { (minHeight, maxHeight) ->
                templates.unsafeReadAs(
                    BlockchainResponse::class.java,
                    "blockchain/${minHeight}-${maxHeight}.json"
                )
            }
            .toTypedArray()

    val s3Config: S3Config = S3Config(bucket = S3Bucket(S3_BUCKET))

    val dynamoConfig: DynamoConfig =
        DynamoConfig(
            region = S3_REGION,
            blockMetadataTable = DynamoTable(DYNAMODB_BLOCK_METADATA_TABLE),
            blockBatchTable = DynamoTable(DYNAMODB_BLOCK_BATCH_TABLE),
            serviceMetadataTable = DynamoTable(DYNAMODB_SERVICE_METADATA_TABLE),
            dynamoBatchGetItems = 100
        )

    val netAdapter: NetAdapter =  okHttpNetAdapter("localhost:26657")

    val decoderAdapter = moshiDecoderAdapter()
}
