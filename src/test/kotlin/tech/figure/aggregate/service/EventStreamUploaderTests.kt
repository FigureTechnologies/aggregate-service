//package tech.figure.aggregate.service
//
//import cloud.localstack.ServiceName
//import cloud.localstack.docker.LocalstackDockerExtension
//import cloud.localstack.docker.annotation.LocalstackDockerProperties
//import com.sksamuel.hoplite.ConfigLoaderBuilder
//import com.sksamuel.hoplite.PropertySource
//import com.sksamuel.hoplite.preprocessor.PropsPreprocessor
//import com.sksamuel.hoplite.sources.EnvironmentVariablesPropertySource
//import io.mockk.coEvery
//import io.mockk.mockk
//import tech.figure.aggregate.common.Config
//import tech.figure.aggregate.common.logger
//import tech.figure.aggregate.common.models.UploadResult
//import tech.figure.aggregate.repository.database.RavenDB
//import tech.figure.aggregate.service.stream.consumers.EventStreamUploader
//import tech.figure.aggregate.service.test.mocks.LocalStackS3
//import tech.figure.aggregate.service.test.mocks.MockAwsClient
//import tech.figure.aggregate.service.test.utils.Defaults
//import kotlinx.coroutines.channels.Channel
//import org.junit.jupiter.api.Test
//import org.junit.jupiter.api.TestInstance
//import org.junit.jupiter.api.extension.ExtendWith
//import org.slf4j.Logger
//import tech.figure.aggregate.service.utils.installShutdownHook
//import kotlinx.coroutines.*
//import kotlinx.coroutines.flow.toList
//import org.junit.jupiter.api.BeforeAll
//import org.junitpioneer.jupiter.SetEnvironmentVariable
//import tech.figure.aggregate.common.Environment
//import tech.figure.aggregate.service.flow.extensions.cancelOnSignal
//import tech.figure.aggregate.service.test.utils.Defaults.blockData
//import tech.figure.aggregate.service.test.utils.Defaults.blockDataIncorrectFormatLive
//import kotlin.time.Duration
//import kotlin.time.ExperimentalTime
//
//@ExperimentalCoroutinesApi
//@TestInstance(TestInstance.Lifecycle.PER_CLASS)
//@ExtendWith(LocalstackDockerExtension::class)
//@LocalstackDockerProperties(services = [ServiceName.S3])
//@SetEnvironmentVariable.SetEnvironmentVariables(
//    SetEnvironmentVariable(
//        key = "ENVIRONMENT",
//        value = "local"
//    ),
//    SetEnvironmentVariable(
//        key = "AWS_ACCESS_KEY_ID",
//        value = "test",
//    ),
//    SetEnvironmentVariable(
//        key = "AWS_SECRET_ACCESS_KEY",
//        value = "test"
//    )
//)
//class EventStreamUploaderTests {
//
//    val log: Logger = logger()
//
//    val aws: MockAwsClient = MockAwsClient.Builder()
//        .build(Defaults.s3Config)
//
//    val ravenClient = mockk<RavenDB>()
//    lateinit var environment: Environment
//
//    lateinit var config: Config
//
//    val shutDownSignal: Channel<Unit> = installShutdownHook(log)
//
//    @BeforeAll
//    fun setup() {
//        environment = runCatching { Environment.valueOf(System.getenv("ENVIRONMENT")) }
//            .getOrElse {
//                error("Not a valid environment: ${System.getenv("ENVIRONMENT")}")
//            }
//        config = ConfigLoaderBuilder.default()
//            .addSource(EnvironmentVariablesPropertySource(useUnderscoresAsSeparator = true, allowUppercaseNames = true))
//            .apply {
//                // If in the local environment, override the ${...} envvar values in `application.properties` with
//                // the values provided in the local-specific `local.env.properties` property file:
//                if (environment.isLocal()) {
//                    addPreprocessor(PropsPreprocessor("/local.env.properties"))
//                }
//            }
//            .addSource(PropertySource.resource("/application.yml"))
//            .build()
//            .loadConfigOrThrow()
//        runBlocking {
//            val s3 = aws.s3() as LocalStackS3
//            s3.createBucket()
//        }
//    }
//
//    @OptIn(ExperimentalTime::class)
//    @Test
//    @SetEnvironmentVariable.SetEnvironmentVariables(
//        SetEnvironmentVariable(
//            key = "ENVIRONMENT",
//            value = "local"
//        )
//    )
//    fun testEventStreamUploaderSuccess() {
//        val blockFlow = blockData()
//        var complete = false
//        runBlocking {
//            coEvery {
//                ravenClient.writeBlockCheckpoint(any())
//            } answers {
//                complete = true
//            }
//            var inspected1 = false
//
//            var uploadResults1: List<UploadResult> = mutableListOf()
//            withTimeoutOrNull(Duration.seconds(4)) {
//                var inspected1 = false
//
//                    uploadResults1 = EventStreamUploader(
//                        blockFlow,
//                        aws,
//                        ravenClient,
//                        "tp",
//                        Pair(config.badBlockRange[0], config.badBlockRange[1]),
//                        config.msgFeeHeight
//                    )
//                        .addExtractor(config.upload.extractors)
//                        .upload { inspected1 = true }
//                        .cancelOnSignal(shutDownSignal)
//                        .toList()
//            }
//            assert(uploadResults1 != null && uploadResults1.isNotEmpty()) { "Stream (1) failed to collect in time" }
//            assert(uploadResults1.size == 5)
//        }
//    }
//
////    @OptIn(ExperimentalTime::class)
////    @Test
////    @SetEnvironmentVariable.SetEnvironmentVariables(
////        SetEnvironmentVariable(
////            key = "ENVIRONMENT",
////            value = "local"
////        )
////    )
////    fun testEventStreamUploaderSuccessIncorrectLiveStructure() {
////        val blockFlow = blockDataIncorrectFormatLive()
////        var complete = false
////        runBlocking {
////            coEvery {
////                ravenClient.writeBlockCheckpoint(any())
////            } answers {
////                complete = true
////            }
////            var inspected1 = false
////
////            var uploadResults1: List<UploadResult> = mutableListOf()
////            withTimeoutOrNull(Duration.seconds(4)) {
////                var inspected1 = false
////
////                    uploadResults1 = EventStreamUploader(
////                        blockFlow,
////                        aws,
////                        ravenClient,
////                        "tp",
////                        Pair(config.badBlockRange[0], config.badBlockRange[1]),
////                        config.msgFeeHeight
////                    )
////                        .addExtractor(config.upload.extractors)
////                        .upload { inspected1 = true }
////                        .cancelOnSignal(shutDownSignal)
////                        .toList()
////            }
////            assert(uploadResults1 != null && uploadResults1.isNotEmpty()) { "Stream (1) failed to collect in time" }
////            assert(uploadResults1.size == 2)
////        }
////    }
//}
