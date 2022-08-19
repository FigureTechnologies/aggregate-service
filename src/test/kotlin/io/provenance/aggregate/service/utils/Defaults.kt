package io.provenance.aggregate.service.test.utils

import com.google.gson.Gson
import io.provenance.aggregate.common.S3Config
import io.provenance.aggregate.common.aws.s3.S3Bucket
import io.provenance.eventstream.stream.clients.BlockData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import java.io.File

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
