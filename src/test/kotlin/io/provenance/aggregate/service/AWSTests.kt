package io.provenance.aggregate.service

import cloud.localstack.ServiceName
import cloud.localstack.docker.LocalstackDockerExtension
import cloud.localstack.docker.annotation.LocalstackDockerProperties
import io.provenance.aggregate.service.base.TestBase
import io.provenance.aggregate.service.mocks.MockAwsInterface
import io.provenance.aggregate.service.mocks.MockTendermintService
import io.provenance.aggregate.service.stream.EventStreamUploader
import io.provenance.aggregate.service.utils.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(LocalstackDockerExtension::class)
@LocalstackDockerProperties(services = [ServiceName.S3, ServiceName.DYNAMO])
class AWSTests : TestBase() {

    val aws = MockAwsInterface(Defaults.s3Config)

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
        runBlocking {
            aws.createBucket()
        }

    }

    @AfterEach
    fun teardownAfterEach() {
        // TODO: Change this to runBlockingTest when issues are fixed. See https://github.com/Kotlin/kotlinx.coroutines/issues/1204
        runBlocking {
            aws.emptyAndDeleteBucket()
        }
    }

    @Test
    fun testStreamBlocksToS3() {
        // TODO: Use this when https://github.com/Kotlin/kotlinx.coroutines/issues/1204 is fixed
        // NOTE! This is possibly a problem when using `.await()` with `CompleteableFuture`, as that is needed when using
        // the AWS SDK v2 async clients
        runBlocking {

            val eventStreamService = Builders.defaultEventStreamService(includeLiveBlocks = true).build()

            val tendermintService = Builders.defaultTendermintService()
                .build(MockTendermintService::class.java)

            val eventStream = Builders.defaultEventStream(
                dispatcherProvider,
                eventStreamService,
                tendermintService,
                skipIfEmpty = true
            )

            val expectTotal = EXPECTED_NONEMPTY_BLOCKS + eventStreamService.expectedResponseCount()

            // We need to explicitly consume EXPECTED_TOTAL_BLOCKS items from teh stream in order to cause it to terminate:
            val es = LimitedEventStream(eventStream, expectTotal.toInt())

            // upload results:
            val collected =
                EventStreamUploader(es, aws, Defaults.moshi, MIN_BLOCK_HEIGHT, dispatchers = dispatcherProvider)
                    .upload()
                    .toList()

            assert(collected.size == expectTotal.toInt())

            // check S3 and make sure there's <expectTotal> objects in the bucket:
            val keys = aws.listBucketObjectKeys()
            assert(keys.isNotEmpty() && keys.size == expectTotal.toInt())
        }
    }
}