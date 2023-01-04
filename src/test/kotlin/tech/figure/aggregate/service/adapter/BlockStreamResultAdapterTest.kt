package tech.figure.aggregate.service.adapter

import com.google.gson.JsonParser
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.google.protobuf.util.JsonFormat
import tech.figure.block.api.proto.BlockServiceOuterClass.BlockStreamResult

class BlockStreamResultAdapterTest: TypeAdapter<BlockStreamResult>() {

    override fun write(jsonWriter: JsonWriter?, value: BlockStreamResult?) {
        jsonWriter?.jsonValue(JsonFormat.printer().print(value))
    }

    override fun read(jsonReader: JsonReader?): BlockStreamResult {
        val blockStreamResultBuilder = BlockStreamResult.newBuilder()
        JsonFormat.parser().merge(JsonParser.parseReader(jsonReader).toString(), blockStreamResultBuilder)
        return blockStreamResultBuilder.build()
    }
}
