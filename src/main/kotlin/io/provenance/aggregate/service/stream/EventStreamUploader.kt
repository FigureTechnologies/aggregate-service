package io.provenance.aggregate.service.stream

import arrow.core.Either
import com.squareup.moshi.Moshi
import io.provenance.aggregate.service.DefaultDispatcherProvider
import io.provenance.aggregate.service.DispatcherProvider
import io.provenance.aggregate.service.adapters.JsonS3Block
import io.provenance.aggregate.service.aws.AwsInterface
import io.provenance.aggregate.service.flow.extensions.*
import io.provenance.aggregate.service.logger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import software.amazon.awssdk.services.s3.model.PutObjectResponse

data class UploadResult(
    val etag: String,
    val streamBlock: StreamBlock
)

class EventStreamUploader(
    private val eventStream: IEventStream,
    private val aws: AwsInterface,
    private val moshi: Moshi,
    private val lastHeight: Long?,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
) {
    constructor(
        eventStreamFactory: EventStream.Factory,
        aws: AwsInterface,
        moshi: Moshi,
        lastHeight: Long?,
        skipEmptyBlocks: Boolean = true
    ) : this(eventStreamFactory.getStream(skipEmptyBlocks), aws, moshi, lastHeight)

    private val log = logger()

    suspend fun upload(concurrentUploads: Int = 10): Flow<UploadResult> {
        return eventStream.streamBlocks(lastHeight)
            .buffer(100, onBufferOverflow = BufferOverflow.SUSPEND)
            .transform {
                when (it) {
                    is Either.Left -> {
                        log.error("${it.value}")
                    }
                    is Either.Right -> {
                        emit(it.value)
                    }
                }
            }
            .chunked(concurrentUploads)
            .transform { streamBlocks ->
                val blockResponses: List<Pair<PutObjectResponse, StreamBlock>> = withContext(dispatchers.io()) {
                    streamBlocks.map { streamBlock: StreamBlock ->
                        async {
                            val response = aws.streamObject(JsonS3Block(streamBlock, moshi))
                            Pair(response, streamBlock)
                        }
                    }
                        .awaitAll()
                }
                for ((s3Response, streamBlock) in blockResponses) {
                    emit(UploadResult(etag = s3Response.eTag(), streamBlock = streamBlock))
                }
            }
    }
}
