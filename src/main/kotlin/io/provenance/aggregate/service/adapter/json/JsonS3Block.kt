package io.provenance.aggregate.service.adapter.json

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import io.provenance.aggregate.service.aws.s3.Keys
import io.provenance.aggregate.service.aws.s3.StreamableObject
import io.provenance.aggregate.service.stream.models.Block
import io.provenance.aggregate.service.stream.models.StreamBlock
import io.provenance.aggregate.service.stream.models.extensions.dateTime
import software.amazon.awssdk.core.async.AsyncRequestBody

class JsonS3Block(block: StreamBlock, moshi: Moshi) : StreamableObject {

    private fun generateKeyPrefix(block: Block) = block.dateTime()?.let { Keys.prefix(it) } ?: "undated"

    val adapter: JsonAdapter<StreamBlock> = moshi.adapter(StreamBlock::class.java)

    override val key = "${generateKeyPrefix(block.block)}/${block.height!!}.json"
    override val body: AsyncRequestBody by lazy {
        AsyncRequestBody.fromString(adapter.toJson(block))
    }
}
