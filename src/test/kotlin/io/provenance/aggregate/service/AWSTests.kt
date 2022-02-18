package io.provenance.aggregate.service.test

import cloud.localstack.ServiceName
import cloud.localstack.docker.LocalstackDockerExtension
import cloud.localstack.docker.annotation.LocalstackDockerProperties
import io.provenance.aggregate.common.aws.dynamodb.client.DefaultDynamoClient
import io.provenance.aggregate.common.aws.dynamodb.client.DynamoClient
import io.provenance.aggregate.service.clients.FailingDynamoDbAsyncClient
import io.provenance.aggregate.common.logger
import io.provenance.aggregate.service.stream.EventStream
import io.provenance.aggregate.service.stream.consumers.EventStreamUploader
import io.provenance.aggregate.common.models.StreamBlock
import io.provenance.aggregate.common.models.UploadResult
import io.provenance.aggregate.service.stream.TendermintServiceClient
import io.provenance.aggregate.service.test.base.TestBase
import io.provenance.aggregate.service.test.mocks.*
import io.provenance.aggregate.service.test.utils.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.extension.ExtendWith
import org.junitpioneer.jupiter.SetEnvironmentVariable
import org.junitpioneer.jupiter.SetEnvironmentVariable.SetEnvironmentVariables
import org.slf4j.Logger
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@Disabled //https://github.com/localstack/localstack/issues/4902
@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(LocalstackDockerExtension::class)
@LocalstackDockerProperties(services = [ServiceName.S3, ServiceName.DYNAMO])
class AWSTests : TestBase() {

    private val log: Logger = logger()

    private val aws: MockAwsClient = MockAwsClient.Builder()
        .build(Defaults.s3Config, Defaults.dynamoConfig)

    private val dynamoClient = aws.dynamoClient

    // Get a view of the AWS S3 interface with more stuff on it needed during testing
    val s3: LocalStackS3 = aws.s3() as LocalStackS3

    // Get a view of the AWS S3 interface with more stuff on it needed during testing
    val dynamo: LocalStackDynamoClient = aws.dynamo() as LocalStackDynamoClient

    val DEFAULT_EXTRACTORS: Array<String> =
        arrayOf("io.provenance.aggregate.service.test.stream.extractors.csv.impl.EventMetdataSessionCreated")

    @BeforeAll
    override fun setup() {
        super.setup()
    }

    @AfterAll
    override fun tearDown() {
        super.tearDown()
    }

    @BeforeEach
    fun setupBeforeEach() {
        // TODO: Change this to runBlockingTest when issues are fixed. See https://github.com/Kotlin/kotlinx.coroutines/issues/1204
        // When run with runBlockingTest: `java.lang.IllegalStateException: This job has not completed yet` will be
        // thrown
        // NOTE! This is possibly a problem when using `.await()` with `CompleteableFuture`, as that is needed when using
        // the AWS SDK v2 async clients
        runBlocking(dispatcherProvider.main()) {
            s3.createBucket()
            dynamo.createTables()
        }
    }

    @AfterEach
    fun teardownAfterEach() {
        // TODO: Change this to runBlockingTest when issues are fixed. See https://github.com/Kotlin/kotlinx.coroutines/issues/1204
        runBlocking(dispatcherProvider.main()) {
            s3.emptyAndDeleteBucket()
            dynamo.dropTables()
        }
    }

    private suspend fun createSimpleEventStream(
        includeLiveBlocks: Boolean = true,
        skipIfEmpty: Boolean = true,
        skipIfSeen: Boolean = true,
        dynamoInterface: DynamoClient = dynamo
    ): Pair<EventStream, Long> {
        val eventStreamService: MockEventStreamService =
            Builders.eventStreamService(includeLiveBlocks = includeLiveBlocks)
                .dispatchers(dispatcherProvider)
                .build()

        val tendermintService: MockTendermintServiceClient = Builders.tendermintService()
            .build(MockTendermintServiceClient::class.java)

        val stream = Builders.eventStream()
            .eventStreamService(eventStreamService)
            .tendermintService(tendermintService)
            .dynamoInterface(dynamoInterface)  // use LocalStack's Dynamo instance:
            .dispatchers(dispatcherProvider)
            .fromHeight(MIN_HISTORICAL_BLOCK_HEIGHT)
            .skipIfEmpty(skipIfEmpty)
            .skipIfSeen(skipIfSeen)
            .build()

        return Pair(stream, EXPECTED_NONEMPTY_BLOCKS + eventStreamService.expectedResponseCount())
    }

    private suspend fun createCustomResponseEventStream(
        includeLiveBlocks: Boolean = true,
        skipIfEmpty: Boolean = true,
        skipIfSeen: Boolean = true,
        dynamoInterface: DynamoClient = dynamo,
        fileName: String
    ): Pair<EventStream, Long> {
        val eventStreamService: MockEventStreamService =
            Builders.eventStreamService(includeLiveBlocks = includeLiveBlocks)
                .dispatchers(dispatcherProvider)
                .build()

        val tendermint: TendermintServiceClient = Builders.tendermintServiceCustom(fileName)
            .build(MockTendermintServiceClient::class.java)

        val stream = Builders.eventStream()
            .eventStreamService(eventStreamService)
            .tendermintService(tendermint)
            .dynamoInterface(dynamoInterface)  // use LocalStack's Dynamo instance:
            .dispatchers(dispatcherProvider)
            .fromHeight(MIN_HISTORICAL_BLOCK_HEIGHT)
            .skipIfEmpty(skipIfEmpty)
            .skipIfSeen(skipIfSeen)
            .build()

        return Pair(stream, EXPECTED_NONEMPTY_BLOCKS + eventStreamService.expectedResponseCount())
    }

    @OptIn(FlowPreview::class, ExperimentalTime::class, ExperimentalCoroutinesApi::class)
    @Test
    @SetEnvironmentVariables(
        SetEnvironmentVariable(
            key = "AWS_ACCESS_KEY_ID",
            value = "test",
        ),
        SetEnvironmentVariable(
            key = "AWS_SECRET_ACCESS_KEY",
            value = "test"
        )
    )
    fun testSimpleStreamBlocksToS3() {

        // TODO: Actually fix this:
        // We need to explicitly disable timeout chunking functionality because it causes the test to hang.
        // This is due to the use of [delay] in the chunk() flow operator used by `EventStreamUploader`.
        val eventStreamOptions = EventStream.Options.DEFAULT.withBatchTimeout(null)

        runBlocking(dispatcherProvider.io()) {

            // There should be no results if no extractors run to actually extract data to upload:
            val (stream0, _) = createSimpleEventStream(includeLiveBlocks = true, skipIfEmpty = true, skipIfSeen = false)
            val uploadResults0: List<UploadResult>? = withTimeoutOrNull(Duration.seconds(10)) {
                EventStreamUploader(
                    stream0,
                    aws,
                    Defaults.moshi,
                    eventStreamOptions,
                    dispatchers = dispatcherProvider
                )
                    .upload()
                    .toList()
            }

            assert(uploadResults0 != null && uploadResults0.isEmpty())
            assert(s3.listBucketObjectKeys().isEmpty())
        }

        runBlocking(dispatcherProvider.io()) {

            // Re-running with an extractor for events that exist in the blocks that are streamed will cause output to
            // be produced. There should be no results if no extractors run to actually extract data to upload:
            val (stream1, expectedTotal1) = createSimpleEventStream(
                includeLiveBlocks = true,
                skipIfEmpty = true,
                skipIfSeen = false
            )
            val uploadResults1: List<UploadResult>? = withTimeoutOrNull(Duration.seconds(10)) {
                EventStreamUploader(
                    stream1,
                    aws,
                    Defaults.moshi,
                    eventStreamOptions,
                    dispatchers = dispatcherProvider
                )
                    .addExtractor(*DEFAULT_EXTRACTORS)
                    .upload()
                    .toList()
            }

            assert((uploadResults1?.sumOf { it.batchSize } ?: 0) == expectedTotal1.toInt()) {
                "EventStreamUploader: Collection timed out (probably waiting for more live blocks that aren't coming)"
            }

            // check S3 and make sure there's <expectTotal> objects in the bucket:
            val keys = s3.listBucketObjectKeys()
            assert(keys.isNotEmpty() && keys.size == (uploadResults1?.size ?: 0))

            // The max height should have been set to that of the last live block received:

            val maxHeight = dynamo.getMaxHistoricalBlockHeight()
            assert(maxHeight!= null  && maxHeight == "2270469".toLong())

            // We should be able to check that maxHeight == MAX_LIVE_BLOCK_HEIGHT. However the order the live and
            // historical streams run in test is non-deterministic. If all of the live streams are received before
            // historical blocks, then the historical stream won't be considered terminated (since it hasn't run yet)
            // and therefore the live stream won't be able to update the maximum height.
            // TODO: figure out how to delay/intersperse results from the live+historical streams
            //assert(maxHeight == MAX_LIVE_BLOCK_HEIGHT)
        }
    }

    @OptIn(FlowPreview::class, ExperimentalTime::class, ExperimentalCoroutinesApi::class)
    @Test
    @SetEnvironmentVariables(
        SetEnvironmentVariable(
            key = "AWS_ACCESS_KEY_ID",
            value = "test",
        ),
        SetEnvironmentVariable(
            key = "AWS_SECRET_ACCESS_KEY",
            value = "test"
        )
    )
    fun testHandlingPreviouslySeenBlocks() {

        // TODO: Actually fix this:
        // We need to explicitly disable timeout chunking functionality because it causes the test to hang.
        // This is due to the use of [delay] in the chunk() flow operator used by `EventStreamUploader`.
        val eventStreamOptions = EventStream.Options.DEFAULT.withBatchTimeout(null)

        runBlocking(dispatcherProvider.io()) {

            // === (CASE 1 -- Never seen) ==============================================================================
            var inspected1 = false
            val uploadResults1: List<UploadResult>? = withTimeoutOrNull(Duration.seconds(10)) {
                val (stream1, _) = createSimpleEventStream(
                    includeLiveBlocks = true,
                    skipIfEmpty = true,
                    skipIfSeen = false
                )
                EventStreamUploader(
                    stream1,
                    aws,
                    Defaults.moshi,
                    eventStreamOptions,
                    dispatchers = dispatcherProvider
                )
                    .addExtractor(*DEFAULT_EXTRACTORS)
                    .upload { block ->
                        // Inspect each block
                        inspected1 = true
                        // There should be no storage metadata attached to any of the blocks because they haven't been
                        // seen yet. Both historical and live blocks won't have any metadata.
                        assert(block.metadata == null)
                    }
                    .toList()
            }
            assert(uploadResults1 != null && uploadResults1.isNotEmpty()) { "Stream (1) failed to collect in time" }
            assert(inspected1) { "Stream 1: no blocks emitted" }

            // === (CASE 2 -- Seen and skipped ) =======================================================================

            // Re-run on a different instance of the stream that's using the same data:
            val blocks2 = mutableListOf<StreamBlock>()
            val (stream2, _) = createSimpleEventStream(includeLiveBlocks = true, skipIfEmpty = true, skipIfSeen = true)
            val uploadResults2: List<UploadResult>? = withTimeoutOrNull(Duration.seconds(10)) {
                EventStreamUploader(
                    stream2,
                    aws,
                    Defaults.moshi,
                    eventStreamOptions,
                    dispatchers = dispatcherProvider
                )
                    .addExtractor(*DEFAULT_EXTRACTORS)
                    .upload { block -> blocks2.add(block) }
                    .toList()
            }
            assert(uploadResults2 != null && uploadResults2.isNotEmpty()) { "Stream (2) failed to collect in time" }
            assert(blocks2.isNotEmpty()) { "Stream (2): no blocks emitted" }
            assert(blocks2.none { it.historical && it.metadata != null }) { "Stream (2) : historical blocks not empty" }
            // "Live" technically haven't been seen, so they will always appear, even if `skipIfSeen` = true
            assert(blocks2.any { !it.historical && it.metadata == null }) { "Stream (2) : live blocks empty" }

            // === (CASE 3 -- Seen and not skipped) ====================================================================

            // Re-run for a third time, but don't skip seen blocks. The returned stream blocks should all have a
            // `BlockStorageMetadata` value, since they've been tracked in Dynamo:
            val blocks3 = mutableListOf<StreamBlock>()
            val (stream3, _) = createSimpleEventStream(includeLiveBlocks = true, skipIfEmpty = true, skipIfSeen = false)
            val uploadResults3: List<UploadResult>? = withTimeoutOrNull(Duration.seconds(10)) {
                EventStreamUploader(
                    stream3,
                    aws,
                    Defaults.moshi,
                    eventStreamOptions,
                    dispatchers = dispatcherProvider
                )
                    .addExtractor(*DEFAULT_EXTRACTORS)
                    .upload { block -> blocks3.add(block) }
                    .toList()
            }
            assert(uploadResults3 != null && uploadResults3.isNotEmpty()) { "Stream (3) failed to collect in time" }
            assert(blocks3.isNotEmpty()) { "Stream (3): no blocks emitted" }
            assert(uploadResults3!!.size == uploadResults1!!.size) { "Stream (3) expected to be the same length as stream (1)" }

            val historicalBlocks = blocks3.filter { it.historical }
            val liveBlocks = blocks3.filter { !it.historical }

            assert(historicalBlocks.isNotEmpty()) { "Stream (3) : historical blocks empty" }
            assert(liveBlocks.isNotEmpty()) { "Stream (3) : live blocks empty" }

            // Historical blocks should have a Dynamo storage metadata entry:
            assert(historicalBlocks.all { it.metadata != null }) { "Stream (3) historical blocks should all have metadata " }
            assert(liveBlocks.all { it.metadata == null }) { "Stream (3) live blocks should not have any metadata " }
        }
    }

    @OptIn(FlowPreview::class, ExperimentalTime::class, ExperimentalCoroutinesApi::class)
    @Test
    @SetEnvironmentVariables(
        SetEnvironmentVariable(
            key = "AWS_ACCESS_KEY_ID",
            value = "test",
        ),
        SetEnvironmentVariable(
            key = "AWS_SECRET_ACCESS_KEY",
            value = "test"
        )
    )
    fun testDynamoRecoversFromCommitFailure() {

        // TODO: Actually fix this:
        // We need to explicitly disable timeout chunking functionality because it causes the test to hang.
        // This is due to the use of [delay] in the chunk() flow operator used by `EventStreamUploader`.
        val eventStreamOptions = EventStream.Options.DEFAULT.withBatchTimeout(null)

        runBlocking(dispatcherProvider.io()) {

            // Fails when `transactWriteItems` is called
            val failingDynamoClient = FailingDynamoDbAsyncClient(aws.dynamoClient) { method, callCount ->
                // First two calls fail:
                val shouldFail = callCount == 2 || callCount == 4 // fail for 2 calls
                log.info("${method}:$callCount :: should fail? $shouldFail")
                shouldFail
            }

            val failingDynamo = object : DefaultDynamoClient(
                failingDynamoClient,
                Defaults.dynamoConfig.blockBatchTable,
                Defaults.dynamoConfig.blockMetadataTable,
                Defaults.dynamoConfig.serviceMetadataTable
            ) {
                // TODO: Replace this when runBlockingTest is fixed:
                // https://github.com/Kotlin/kotlinx.coroutines/pull/2978
                override suspend fun doDelay(duration: Duration) {
                    log.info("Faking delay for ${duration.inWholeMilliseconds} milliseconds")
                }
            }

            val failingAws: MockAwsClient = MockAwsClient.builder()
                .dynamoImplementation(failingDynamo)
                .build()

            val uploadResults: List<UploadResult>? =
                withTimeoutOrNull(Duration.seconds(10)) {
                    val (stream, _) = createSimpleEventStream(
                        includeLiveBlocks = true,
                        skipIfEmpty = true,
                        skipIfSeen = false,
                        dynamoInterface = failingDynamo
                    )
                    EventStreamUploader(
                        stream,
                        failingAws,
                        Defaults.moshi,
                        eventStreamOptions,
                        dispatchers = dispatcherProvider
                    )
                        .addExtractor(*DEFAULT_EXTRACTORS)
                        .upload()
                        .toList()
                }

            assert(uploadResults != null && uploadResults.isNotEmpty()) { "Stream failed to collect in time" }
        }
    }

    @OptIn(FlowPreview::class, ExperimentalTime::class, ExperimentalCoroutinesApi::class)
    @Test
    @SetEnvironmentVariables(
        SetEnvironmentVariable(
            key = "AWS_ACCESS_KEY_ID",
            value = "test",
        ),
        SetEnvironmentVariable(
            key = "AWS_SECRET_ACCESS_KEY",
            value = "test"
        )
    )
    fun testDynamoFailsForTooManyConsecutiveCommitFailures() {

        // TODO: Actually fix this:
        // We need to explicitly disable timeout chunking functionality because it causes the test to hang.
        // This is due to the use of [delay] in the chunk() flow operator used by `EventStreamUploader`.
        val eventStreamOptions = EventStream.Options.DEFAULT.withBatchTimeout(null)

        assertThrows<TransactionCanceledException> {

            runBlocking(dispatcherProvider.main()) {

                // Fails when `transactWriteItems` is called
                val failingDynamoClient = FailingDynamoDbAsyncClient(aws.dynamoClient) { method, callCount ->
                    // Make all calls fail, exceeding the reattempt threshold `DYNAMODB_MAX_TRANSACTION_RETRIES`:
                    true
                }

                val failingDynamo = object : DefaultDynamoClient(
                    failingDynamoClient,
                    Defaults.dynamoConfig.blockBatchTable,
                    Defaults.dynamoConfig.blockMetadataTable,
                    Defaults.dynamoConfig.serviceMetadataTable
                ) {
                    // TODO: Replace this when runBlockingTest is fixed:
                    // https://github.com/Kotlin/kotlinx.coroutines/pull/2978
                    override suspend fun doDelay(duration: Duration) {
                        log.info("Faking delay for ${duration.inWholeMilliseconds} milliseconds")
                    }
                }

                val failingAws: MockAwsClient = MockAwsClient.builder()
                    .dynamoImplementation(failingDynamo)
                    .build()

                withTimeoutOrNull(Duration.seconds(10)) {
                    val (stream, _) = createSimpleEventStream(
                        includeLiveBlocks = true,
                        skipIfEmpty = true,
                        skipIfSeen = false,
                        dynamoInterface = failingDynamo
                    )
                    EventStreamUploader(
                        stream,
                        failingAws,
                        Defaults.moshi,
                        eventStreamOptions,
                        dispatchers = dispatcherProvider
                    )
                        .addExtractor(*DEFAULT_EXTRACTORS)
                        .upload()
                        .toList()
                }
            }
        }
    }

    @OptIn(FlowPreview::class, ExperimentalTime::class, ExperimentalCoroutinesApi::class)
    @Test
    @SetEnvironmentVariables(
        SetEnvironmentVariable(
            key = "AWS_ACCESS_KEY_ID",
            value = "test",
        ),
        SetEnvironmentVariable(
            key = "AWS_SECRET_ACCESS_KEY",
            value = "test"
        )
    )
    fun testMalformedAmountBeforeUpload() {
        runBlocking(dispatcherProvider.io()) {

            val block1 = mutableListOf<StreamBlock>()

            // Re-running with an extractor for events that exist in the blocks that are streamed will cause output to
            // be produced. There should be no results if no extractors run to actually extract data to upload:
            val (stream1, expectedTotal1) = createCustomResponseEventStream(
                includeLiveBlocks = false,
                skipIfEmpty = true,
                skipIfSeen = false,
                fileName = "MalformedAmount.json"
            )

            val uploadResults1: List<UploadResult>? = withTimeoutOrNull(Duration.seconds(10)) {
                EventStreamUploader(
                    stream1,
                    aws,
                    Defaults.moshi,
                    EventStream.Options.DEFAULT.withBatchTimeout(null),
                    dispatchers = dispatcherProvider
                )
                    .addExtractor("io.provenance.aggregate.service.stream.extractors.csv.impl.TxCoinTransfer")
                    .upload{ block -> block1.add(block) }
                    .toList()
            }

            val record = s3.readContent(s3.listBucketObjectKeys()[0])
            val amount1 = record[1].get(7).plus(record[1].get(8))
            val amount2 = record[2].get(7).plus(record[2].get(8))

            assert(amount1 == "53126cfigurepayomni")
            assert(amount2 == "10000000000nhash")
        }
    }
}
