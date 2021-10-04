package io.provenance.aggregate.service.stream.models.rpc.response.decoder

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.provenance.aggregate.service.stream.NewBlockResult
import io.provenance.aggregate.service.stream.models.rpc.response.MessageType
import io.provenance.aggregate.service.stream.models.rpc.response.RpcResponse

class NewBlockDecoder(moshi: Moshi) : Decoder(moshi) {

    override val priority: Int = 100

    // We have to build a reified, parameterized type suitable to pass to `moshi.adapter`
    // because it's not possible to do something like `RpcResponse<NewBlockResult>::class.java`:
    // See https://stackoverflow.com/questions/46193355/moshi-generic-type-adapter
    private val adapter: JsonAdapter<RpcResponse<NewBlockResult>> = moshi.adapter(
        Types.newParameterizedType(RpcResponse::class.java, NewBlockResult::class.java)
    )

    override fun decode(input: String): MessageType.NewBlock? {
        return adapter.fromJson(input)?.let { it.result?.let { block -> MessageType.NewBlock(block) } }
    }
}