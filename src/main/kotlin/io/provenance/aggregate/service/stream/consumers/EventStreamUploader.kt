package io.provenance.aggregate.service.stream.consumers

import io.provenance.aggregate.common.aws.AwsClient
import io.provenance.aggregate.common.aws.dynamodb.BlockBatch
import io.provenance.aggregate.common.aws.dynamodb.WriteResult
import io.provenance.aggregate.common.aws.dynamodb.client.DynamoClient
import io.provenance.aggregate.common.aws.s3.S3Key
import io.provenance.aggregate.common.aws.s3.StreamableObject
import io.provenance.aggregate.common.aws.s3.client.S3Client
import io.provenance.aggregate.common.logger
import io.provenance.aggregate.common.models.BatchId
import io.provenance.aggregate.common.models.UploadResult
import io.provenance.aggregate.common.models.extensions.toStreamBlock
import io.provenance.eventstream.stream.BlockStreamOptions
import io.provenance.aggregate.repository.RepositoryBase
import io.provenance.aggregate.service.flow.extensions.chunked
import io.provenance.aggregate.service.stream.batch.Batch
import io.provenance.aggregate.service.stream.extractors.Extractor
import io.provenance.aggregate.service.stream.extractors.OutputType
import io.provenance.eventstream.coroutines.DefaultDispatcherProvider
import io.provenance.eventstream.coroutines.DispatcherProvider
import io.provenance.eventstream.stream.clients.BlockData
import io.provenance.eventstream.stream.models.StreamBlock
import io.provenance.eventstream.stream.models.extensions.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import java.time.OffsetDateTime
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * An event stream consumer responsible for uploading streamed blocks to S3.
 *
 * @property The event stream which provides blocks to this consumer.
 * @property aws The client used to interact with AWS.
 * @property decoderAdapter The JSON serializer/deserializer used by this consumer.
 * @property options Options used to configure this consumer.
 * @property dispatchers The coroutine dispatchers used to run asynchronous tasks in this consumer.
 * @p
 */
@OptIn(FlowPreview::class, ExperimentalTime::class)
@ExperimentalCoroutinesApi
class EventStreamUploader(
    private val blockFlow: Flow<BlockData>,
    private val aws: AwsClient,
    private val repository: RepositoryBase,
    private val options: BlockStreamOptions,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
) {

    companion object {
        const val STREAM_BUFFER_CAPACITY: Int = 256
    }

    private val log = logger()

    private val extractorClassNames: MutableList<String> = mutableListOf()

    private fun csvS3Key(id: BatchId, d: OffsetDateTime?, label: String): S3Key =
        S3Key.create(d ?: OffsetDateTime.MIN, id.value, "$label.csv")

    /**
     * The fully-qualified class names of the Class instances to load.
     *
     * If `throwOnError` is false, classes that cannot be loaded will be omitted from the output and a message will
     * be logged.
     */
    @Suppress("UNCHECKED_CAST")
    private fun loadExtractorClasses(
        fqClassNames: List<String>,
        throwOnError: Boolean = false
    ): List<KClass<out Extractor>> {
        val kclasses = mutableListOf<KClass<out Extractor>>()
        for (className in fqClassNames) {
            try {
                val cls: KClass<out Extractor> = Class.forName(className).kotlin as KClass<out Extractor>
                kclasses.add(cls)
            } catch (e: Exception) {
                if (throwOnError) {
                    throw e
                } else {
                    log.error("failed to load extractor class: $className; skipping")
                }
            }
        }
        return kclasses
    }

    /**
     * Add one or more fully-qualified extractor classes to use when extracting data from blocks streamed from
     * event stream.
     *
     * Classes are expected to:
     *  - Be fully qualified class names.
     *      Example: "io.provenance.aggregate.service.stream.extractors.csv.impl.TxEventAttributes"
     *  - Implement the `Extractor` interface.
     *
     *  @see [Extractor]
     *  @see [Batch.Builder.withExtractor]
     */
    fun addExtractor(vararg fqClassName: String): EventStreamUploader = apply {
        extractorClassNames.addAll(fqClassName)
    }

    fun addExtractor(fqClassNames: Iterable<String>): EventStreamUploader = apply {
        extractorClassNames.addAll(fqClassNames)
    }

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
     *  @property onEachBlock An optional callback that will be run for each block as it is received from the stream.
     *    This is useful for inspecting blocks prior to the block being processed.
     *  @return A flow yielding the results of uploading blocks to S3.
     */
    suspend fun upload(onEachBlock: (StreamBlock) -> Unit = {}): Flow<UploadResult> {
        val s3: S3Client = aws.s3()
        val dynamo: DynamoClient = aws.dynamo()

        val batchBlueprint: Batch.Builder = Batch.Builder()
            .dispatchers(dispatchers)
            // Extractors are specified via their Kotlin class, along with any arguments to pass to the constructor:
            .apply {
                for (cls in loadExtractorClasses(extractorClassNames)) {
                    withExtractor(cls)
                }
            }

        return blockFlow
            .transform { emit(it.toStreamBlock()) }
            .filter { streamBlock ->
                !streamBlock.blockResult.isNullOrEmpty().also {
                    log.info(
                        if(streamBlock.blockResult.isNullOrEmpty())
                            "Skipping empty block: ${streamBlock.height}"
                        else
                            "Buffering block: ${streamBlock.height} Tx Result Size: ${streamBlock.blockResult!!.size} Tx Event Size: ${streamBlock.txEvents.size}"
                    )
                }
            }
            .buffer(STREAM_BUFFER_CAPACITY, onBufferOverflow = BufferOverflow.SUSPEND)
            .flowOn(dispatchers.io())
            .chunked(size = options.batchSize, timeout = 10.seconds)
            .transform { streamBlocks: List<StreamBlock> ->
                log.info("collected block chunk(size=${streamBlocks.size}) and preparing for upload")
                val batch: Batch = batchBlueprint.build()

                runBlocking(dispatchers.io()) {
                    launch {
                        streamBlocks.map { block ->
                            repository.saveBlock(block)
                        }
                    }
                }

                // Use the earliest block date to generate the S3 key prefix the data files will be stored under:
                val earliestDate: OffsetDateTime? =
                    streamBlocks.mapNotNull { block -> block.block.dateTime() }.minOrNull()

                // Run the extract steps:
                val uploaded: List<UploadResult> = coroutineScope {
                    withContext(dispatchers.io()) {
                        streamBlocks.map { block ->
                            onEachBlock(block)
                            async { batch.processBlock(block) }
                        }.awaitAll()

                        // Upload the results to S3:
                        batch.complete { batchId: BatchId, extractor: Extractor ->
                            val key: S3Key = csvS3Key(batchId, earliestDate, extractor.name)
                            when (val out = extractor.output()) {
                                is OutputType.FilePath -> {
                                    if (extractor.shouldOutput()) {
                                        val putResponse: PutObjectResponse = s3.streamObject(object : StreamableObject {
                                            override val key: S3Key get() = key
                                            override val body: AsyncRequestBody get() = AsyncRequestBody.fromFile(out.path)
                                            override val metadata: Map<String, String>? get() = out.metadata
                                        })

                                        /**
                                         * We want to track the last successful block height that was processed to S3, so
                                         * in the event of an exception to the service, the service can restart
                                         * at the last successful processed block height, so we don't lose any data that
                                         * was in the middle of processing.
                                         */
                                        val highestBlockHeight = streamBlocks.last().height
                                        val lowestBlockHeight = streamBlocks.first().height

                                        if (putResponse.sdkHttpResponse().isSuccessful) {
                                            dynamo.writeMaxHistoricalBlockHeight(highestBlockHeight!!)
                                                .also {
                                                    if (it.processed > 0) {
                                                        log.info("Checkpoint::Updating max block height to = $highestBlockHeight")
                                                    }
                                                }
                                            log.info("dest = ${aws.s3Config.bucket}/$key; eTag = ${putResponse.eTag()}")
                                        }

                                        UploadResult(
                                            batchId = batch.id,
                                            batchSize = streamBlocks.size,
                                            eTag = putResponse.eTag(),
                                            s3Key = key,
                                            blockHeightRange = Pair(lowestBlockHeight, highestBlockHeight)
                                        )
                                    } else {
                                        null
                                    }
                                }
                                else -> null
                            }
                        }
                            .filterNotNull()
                    }
                }

                // Mark the blocks as having been processed:
                val s3Keys = uploaded.map { it.s3Key }
                val blockBatch = BlockBatch(batch.id, aws.s3Config.bucket, s3Keys)
                val writeResult: WriteResult = dynamo.trackBlocks(blockBatch, streamBlocks)
                log.info("track block batch result: $writeResult")

                //  At this point, signal success by emitting results:
                emitAll(uploaded.asFlow())
            }
            .flowOn(dispatchers.io())
    }
}
