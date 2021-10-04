package io.provenance.aggregate.service.stream.models.rpc.response.decoder

import com.squareup.moshi.Moshi
import io.provenance.aggregate.service.stream.models.rpc.response.MessageType

sealed class Decoder(val moshi: Moshi) {
    abstract val priority: Int
    abstract fun decode(input: String): MessageType?
}