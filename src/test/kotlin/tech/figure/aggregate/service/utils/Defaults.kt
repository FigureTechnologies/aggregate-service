package tech.figure.aggregate.service.test.utils

import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import tech.figure.aggregate.service.adapter.BlockStreamResultAdapterTest
import tech.figure.block.api.proto.BlockServiceOuterClass.BlockStreamResult
import java.io.File

object Defaults {

    fun blockData(): Flow<BlockStreamResult> =
        heights
            .map {
                val reader = File("src/test/resources/templates/block_data/448484.json").bufferedReader()
                GsonBuilder().registerTypeAdapter(BlockStreamResult::class.java, BlockStreamResultAdapterTest()).create()
                    .fromJson(reader, BlockStreamResult::class.java)
            }
            .asFlow()
}
