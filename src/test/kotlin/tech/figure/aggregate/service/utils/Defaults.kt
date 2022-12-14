package tech.figure.aggregate.service.test.utils

import com.google.gson.Gson
import tech.figure.aggregate.common.S3Config
import tech.figure.aggregate.common.aws.s3.S3Bucket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import tech.figure.block.api.proto.BlockServiceOuterClass
import java.io.File

object Defaults {

    fun blockData(): Flow<BlockServiceOuterClass.BlockStreamResult> =
        heights
            .map {
                val reader = File("src/test/resources/templates/block_data/448484.json").bufferedReader()
                Gson().fromJson(reader, BlockServiceOuterClass.BlockStreamResult::class.java)
            }
            .asFlow()

    fun blockDataIncorrectFormatLive(): Flow<BlockServiceOuterClass.BlockStreamResult> =
        heights
            .map {
                var path = "src/test/resources/templates/block_data/448484.json"
//                if (it >= MIN_LIVE_BLOCK_HEIGHT) {
//                    path = "src/test/resources/templates/incorrect_structure/${it}.json"
//                }
                val reader = File(path).bufferedReader()
                Gson().fromJson(reader, BlockServiceOuterClass.BlockStreamResult::class.java)
            }
            .asFlow()

    val s3Config: S3Config = S3Config(bucket = S3Bucket(S3_BUCKET))
}
