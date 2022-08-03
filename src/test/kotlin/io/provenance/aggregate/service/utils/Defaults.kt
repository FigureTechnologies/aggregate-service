package io.provenance.aggregate.service.test.utils

import com.google.gson.Gson
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
import io.provenance.eventstream.stream.clients.BlockData
import io.reactivex.rxkotlin.toFlowable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

object Defaults {

    fun blockData(): Flow<BlockData> =
        heights
            .map {
                val reader = File("src/test/resources/templates/block_data/${it}.json").bufferedReader()
                Gson().fromJson(reader, BlockData::class.java)
            }
            .asFlow()

    fun blockDataIncorrectFormatLive(): Flow<BlockData> =
        heights
            .map {
                var path = "src/test/resources/templates/block_data/${it}.json"
                if (it >= MIN_LIVE_BLOCK_HEIGHT) {
                    path = "src/test/resources/templates/incorrect_structure/${it}.json"
                }
                val reader = File(path).bufferedReader()
                Gson().fromJson(reader, BlockData::class.java)
            }
            .asFlow()

    val s3Config: S3Config = S3Config(bucket = S3Bucket(S3_BUCKET))
}
