package io.provenance.aggregate.service.stream

import arrow.core.Either
import com.squareup.moshi.Moshi
import io.provenance.aggregate.service.DefaultDispatcherProvider
import io.provenance.aggregate.service.DispatcherProvider
import io.provenance.aggregate.service.adapters.JsonS3Block
import io.provenance.aggregate.service.aws.AwsInterface
import io.provenance.aggregate.service.aws.dynamodb.AwsDynamoInterface
import io.provenance.aggregate.service.aws.dynamodb.WriteResult
import io.provenance.aggregate.service.aws.s3.AwsS3Interface
import io.provenance.aggregate.service.flow.extensions.*
import io.provenance.aggregate.service.logger
import io.provenance.aggregate.service.stream.models.StreamBlock
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import software.amazon.awssdk.services.s3.model.PutObjectResponse

data class UploadResult(
    val etag: String,
    val streamBlock: StreamBlock
)

@OptIn(FlowPreview::class)
@ExperimentalCoroutinesApi
class EventStreamUploader(
    private val eventStream: IEventStream,
    private val aws: AwsInterface,
    private val moshi: Moshi,
    private val options: EventStream.Options = EventStream.Options.DEFAULT,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
) {
    constructor(
        eventStreamFactory: EventStream.Factory,
        aws: AwsInterface,
        moshi: Moshi,
        options: EventStream.Options
    ) : this(eventStreamFactory.create(options), aws, moshi, options)

    companion object {
        const val STREAM_BUFFER_CAPACITY: Int = 100
    }

    private val log = logger()

    suspend fun upload(concurrentUploads: Int = options.concurrency): Flow<UploadResult> {

        val s3: AwsS3Interface = aws.s3()
        val dynamo: AwsDynamoInterface = aws.dynamo()

        return eventStream.streamBlocks()
            .buffer(STREAM_BUFFER_CAPACITY, onBufferOverflow = BufferOverflow.SUSPEND)
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
            .onEach { log.info("buffering live block #${it.block.header?.height} for upload") }
            .chunked(concurrentUploads)
            .transform { streamBlocks: List<StreamBlock> ->

                val responses: List<Pair<PutObjectResponse, StreamBlock>> = coroutineScope {
                    streamBlocks.map { streamBlock: StreamBlock ->
                        async {
                            val response = s3.streamObject(JsonS3Block(streamBlock, moshi))
                            Pair(response, streamBlock)
                        }
                    }
                        .awaitAll()
                }

                // Upload to S3 and mark the block as being uploaded:
                for ((s3Response, streamBlock) in responses) {
                    emit(UploadResult(etag = s3Response.eTag(), streamBlock = streamBlock))
                }

                val markBlockHeights: List<Long> = responses.mapNotNull { it.second.height }
                val writeResult: WriteResult = dynamo.markBlocks(markBlockHeights)
                log.info("S3 write result: ${writeResult} ***")

            }
            .flowOn(dispatchers.io())
    }
}
