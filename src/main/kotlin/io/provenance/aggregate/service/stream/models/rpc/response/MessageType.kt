package io.provenance.aggregate.service.stream.models.rpc.response

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import io.provenance.aggregate.service.stream.NewBlockResult
import kotlin.reflect.full.primaryConstructor
import io.provenance.aggregate.service.stream.models.rpc.response.decoder.Decoder as TDecoder

sealed interface MessageType {
    /**
     * Decode the supplied input into one of the variants of `MessageType`
     */
    class Decoder(val moshi: Moshi) {
        // Decoders are attempted according to their assigned priority in descending order:
        private val decoders =
            TDecoder::class.sealedSubclasses.mapNotNull { clazz -> clazz.primaryConstructor?.call(moshi) }
                .sortedByDescending { it.priority }

        fun decode(input: String): MessageType {
            for (decoder in decoders) {
                try {
                    val message = decoder.decode(input)
                    if (message != null) {
                        return message
                    }
                } catch (_: JsonDataException) {
                }
            }
            return Unknown
        }
    }

    object Unknown : MessageType
    object Empty : MessageType
    data class Error(val error: RpcError) : MessageType
    data class Panic(val error: RpcError) : MessageType
    data class NewBlock(val block: NewBlockResult) : MessageType
}