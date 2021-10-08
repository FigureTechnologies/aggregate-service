package io.provenance.aggregate.service.test

import cloud.localstack.ServiceName
import cloud.localstack.docker.LocalstackDockerExtension
import cloud.localstack.docker.annotation.LocalstackDockerProperties
import io.provenance.aggregate.service.stream.EventStream
import io.provenance.aggregate.service.stream.EventStreamUploader
import io.provenance.aggregate.service.stream.models.StreamBlock
import io.provenance.aggregate.service.stream.models.UploadResult
import io.provenance.aggregate.service.test.base.TestBase
import io.provenance.aggregate.service.test.mocks.*
import io.provenance.aggregate.service.test.utils.Builders
import io.provenance.aggregate.service.test.utils.Defaults
import io.provenance.aggregate.service.test.utils.EXPECTED_NONEMPTY_BLOCKS
import io.provenance.aggregate.service.test.utils.MIN_BLOCK_HEIGHT
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.junitpioneer.jupiter.SetEnvironmentVariable
import org.junitpioneer.jupiter.SetEnvironmentVariable.SetEnvironmentVariables
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(LocalstackDockerExtension::class)
@LocalstackDockerProperties(services = [ServiceName.S3, ServiceName.DYNAMO])
class AWSTests : TestBase() {

    private val aws: MockAwsInterface = MockAwsInterface.builder()
        .build(Defaults.s3Config, Defaults.dynamoConfig)

    // Get a view of the AWS S3 interface with more stuff on it needed during testing
    val s3: LocalStackS3 = aws.s3() as LocalStackS3

    // Get a view of the AWS S3 interface with more stuff on it needed during testing
    val dynamo: LocalStackDynamo = aws.dynamo() as LocalStackDynamo

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
    ): Pair<EventStream, Long> {
        val eventStreamService: MockEventStreamService =
            Builders.eventStreamService(includeLiveBlocks = includeLiveBlocks)
                .dispatchers(dispatcherProvider)
                .build()

        val tendermintService: MockTendermintService = Builders.tendermintService()
            .build(MockTendermintService::class.java)

        val stream = Builders.eventStream()
            .eventStreamService(eventStreamService)
            .tendermintService(tendermintService)
            .dynamoInterface(dynamo)  // use LocalStack's Dynamo instance:
            .dispatchers(dispatcherProvider)
            .fromHeight(MIN_BLOCK_HEIGHT)
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
        // TODO: Use this when https://github.com/Kotlin/kotlinx.coroutines/issues/1204 is fixed
        // NOTE! This is possibly a problem when using `.await()` with `CompleteableFuture`, as that is needed when using
        // the AWS SDK v2 async clients
        runBlocking(dispatcherProvider.main()) {

            // There should be no results if no extractors run to actually extract data to upload:
            val (stream0, _) = createSimpleEventStream(includeLiveBlocks = true, skipIfEmpty = true, skipIfSeen = false)
            val uploadResults0: List<UploadResult>? = withTimeoutOrNull(Duration.seconds(10)) {
                EventStreamUploader(
                    stream0,
                    aws,
                    Defaults.moshi,
                    EventStream.Options.DEFAULT,
                    dispatchers = dispatcherProvider
                )
                    .upload()
                    .toList()
            }

            assert(uploadResults0 != null && uploadResults0.isEmpty())
            assert(s3.listBucketObjectKeys().isEmpty())

            // Re-running with an extractor for events that exist in the blocks that are streamed will cause output to
            // be produced. There should be no results if no extractors run to actually extract data to upload:
            val (stream1, expectedTotal1) = createSimpleEventStream(
                includeLiveBlocks = true,
                skipIfEmpty = true,
                skipIfSeen = false
            )
            val uploadResults1: List<UploadResult>? = withTimeoutOrNull(Duration.seconds(15)) {
                EventStreamUploader(
                    stream1,
                    aws,
                    Defaults.moshi,
                    EventStream.Options.DEFAULT,
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

            // The max height should have been set:
            val maxHeight = dynamo.getMaxHistoricalBlockHeight()
            assert(dynamo.getMaxHistoricalBlockHeight() != null && maxHeight is Long)
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

        runBlocking(dispatcherProvider.main()) {

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
                    EventStream.Options.DEFAULT,
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
                    EventStream.Options.DEFAULT,
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
                    EventStream.Options.DEFAULT,
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
}
