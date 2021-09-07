package io.provenance.aggregate.service.adapters

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import io.provenance.aggregate.service.aws.S3StreamableObject
import io.provenance.aggregate.service.stream.StreamBlock
import io.provenance.aggregate.service.stream.models.Block
import io.provenance.aggregate.service.stream.models.extensions.dateTime
import software.amazon.awssdk.core.async.AsyncRequestBody

internal fun generateKeyPrefix(block: Block) =
    block.dateTime()
        ?.let {
            "${it.year}/${it.month.value}/${it.dayOfMonth}/${it.hour}"
        } ?: "undated"

class JsonS3Block(block: StreamBlock, moshi: Moshi) : S3StreamableObject {
    val adapter: JsonAdapter<StreamBlock> = moshi.adapter(StreamBlock::class.java)

    override val key = "${generateKeyPrefix(block.block)}/${block.height!!}.json"
    override val body = AsyncRequestBody.fromString(adapter.toJson(block))
}