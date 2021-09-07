package io.provenance.aggregate.service.stream

import arrow.core.Either
import com.squareup.moshi.Moshi
import io.provenance.aggregate.service.adapters.JsonS3Block
import io.provenance.aggregate.service.aws.AwsInterface
import io.provenance.aggregate.service.flow.extensions.*
import io.provenance.aggregate.service.logger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.transform

class EventStreamUploader(
    private val eventStreamFactory: EventStream.Factory,
    private val aws: AwsInterface,
    private val moshi: Moshi,
    private val lastHeight: Long?,
    private val skipEmptyBlocks: Boolean = true
) {
    private val log = logger()

    suspend fun upload() {
        val eventStream = eventStreamFactory.getStream(skipEmptyBlocks)

        eventStream.streamBlocks(lastHeight)
            .buffer(100, onBufferOverflow = BufferOverflow.SUSPEND)
            .transform {
                when (it) {
                    is Either.Left -> {
                        log.error("${it.value}")
                    }
                    is Either.Right -> {
                        log.info("Received block")
                        emit(it.value)
                    }
                }
            }
            .chunked(10)
            .collect { streamBlocks ->
                val responses = withContext(Dispatchers.IO) {
                    coroutineScope {
                        streamBlocks.map { streamBlock: StreamBlock ->
                            async {
                                aws.streamObject(JsonS3Block(streamBlock, moshi))
                            }
                        }
                    }
                        .awaitAll()
                }
                for (response in responses) {
                    println("RESPONSE = $response.")
                }
            }
    }
}
