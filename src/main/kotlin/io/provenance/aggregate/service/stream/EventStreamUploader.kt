package io.provenance.aggregate.service.stream

import com.squareup.moshi.Moshi
import io.provenance.aggregate.service.DefaultDispatcherProvider
import io.provenance.aggregate.service.DispatcherProvider
import io.provenance.aggregate.service.aws.AwsInterface
import io.provenance.aggregate.service.aws.dynamodb.AwsDynamoInterface
import io.provenance.aggregate.service.aws.dynamodb.WriteResult
import io.provenance.aggregate.service.aws.s3.AwsS3Interface
import io.provenance.aggregate.service.aws.s3.Keys
import io.provenance.aggregate.service.aws.s3.StreamableObject
import io.provenance.aggregate.service.flow.extensions.chunked
import io.provenance.aggregate.service.logger
import io.provenance.aggregate.service.stream.batch.Batch
import io.provenance.aggregate.service.stream.batch.BatchId
import io.provenance.aggregate.service.stream.extractors.OutputType
import io.provenance.aggregate.service.stream.models.StreamBlock
import io.provenance.aggregate.service.stream.models.UploadResult
import io.provenance.aggregate.service.stream.models.extensions.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import java.time.OffsetDateTime

@OptIn(FlowPreview::class)
@ExperimentalCoroutinesApi
class EventStreamUploader(
    private val eventStream: EventStream,
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
        const val STREAM_BUFFER_CAPACITY: Int = 256
    }

    private val log = logger()

    private fun csvS3Key(id: BatchId, d: OffsetDateTime?, label: String) =
        "${d?.run { Keys.prefix(this) } ?: "undated"}/${id}/${label}.csv"

    /**
     * Run the upload action. The steps for uploading are as follows:
     *
     * (1) Stream each block and assign some division of blocks to a batch, designated by a batch ID.
     *     Batches can contain up to `B` blocks.
     *
     * (2) Process each block with every extractor that's set up to run as part of a batch.
     *
     * (3) Each extractor has a label associated with it. After all blocks are processed, collect the results
     *     of running each block through the extractor.
     *
     *     Note: for `N` extractors, there should be `N` output files generated.
     *     Each output file is generated from processing `B` blocks, but might contain greater or fewer than `B` lines
     *     depending on the implementation of the extractor that produced it.
     *
     * (4) Upload the output files into the S3 bucket specified in the application configuration file.
     *     In order to prevent too many files from occupying the same S3 key prefix, files are segmented and stored
     *     according to the key convention:
     *
     *     "${EARLIEST-BLOCK-DATE}/${BATCH-ID}/${EXTRACTOR-LABEL}.csv"
     *
     *     where `${EARLIEST-BLOCK-DATE}` corresponds to the earliest block in the batch and is formatted as:
     *
     *     "${YEAR}/${MONTH}/${DAY}/${HOUR}"
     *
     *  @param onEachBlock An optional callback that will be run for each block as it is received from the stream.
     *    This is useful for inspecting blocks prior to the block being processed.
     */
    suspend fun upload(onEachBlock: (StreamBlock) -> Unit = {}): Flow<UploadResult> {

        val s3: AwsS3Interface = aws.s3()
        val dynamo: AwsDynamoInterface = aws.dynamo()

        val batchBlueprint: Batch.Builder = Batch.Builder()
            .dispatchers(dispatchers)
            // Extractors are specified via their Kotlin class (KClass), along with any arguments to pass to the
            // constructor:
            .withExtractor(io.provenance.aggregate.service.stream.extractors.csv.TxEventAttributes::class, s3)

        return eventStream.streamBlocks()
            .onEach {
                log.info("buffering block #${it.block.header?.height} (live=${!it.historical}) for upload")
            }
            .buffer(STREAM_BUFFER_CAPACITY, onBufferOverflow = BufferOverflow.SUSPEND)
            .chunked(options.batchSize)
            .transform { streamBlocks: List<StreamBlock> ->

                log.info("collected block chunk(size=${streamBlocks.size}; preparing for upload")

                val batch: Batch = batchBlueprint.build()

                // Use the earliest block date to generate the S3 key prefix the data files will be stored under:
                val earliestDate: OffsetDateTime? =
                    streamBlocks.mapNotNull { block -> block.block.dateTime() }.minOrNull()

                // Run the extract steps:
                coroutineScope {
                    streamBlocks.map { block ->
                        onEachBlock(block)
                        async { batch.processBlock(block) }
                    }
                }
                    .awaitAll()

                // Upload the results to S3:
                val uploaded: List<UploadResult> = batch.complete { batchId, extractor ->
                    val s3Key: String = csvS3Key(batchId, earliestDate, extractor.name)
                    when (val out = extractor.output()) {
                        is OutputType.FilePath -> {
                            val putResponse: PutObjectResponse = s3.streamObject(object : StreamableObject {
                                override val key: String get() = s3Key
                                override val body: AsyncRequestBody get() = AsyncRequestBody.fromFile(out.path)
                            })
                            log.info("${batchId}/${extractor.name} => put.eTag = ${putResponse.eTag()}")
                            UploadResult(
                                batchId = batch.id,
                                batchSize = streamBlocks.size,
                                eTag = putResponse.eTag(),
                                s3Key = s3Key
                            )
                        }
                        else -> null
                    }
                }.filterNotNull()

                // Mark the blocks as having been processed:
                val writeResult: WriteResult = dynamo.trackBlocks(batch.id, streamBlocks)
                log.info("Dynamo write result: $writeResult")

                //  At this point, signal success by emitting results:
                emitAll(uploaded.asFlow())
            }
            .flowOn(dispatchers.io())
    }
}
