package tech.figure.aggregate.service

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.PropertySource
import com.sksamuel.hoplite.preprocessor.PropsPreprocessor
import com.sksamuel.hoplite.sources.EnvironmentVariablesPropertySource
import io.mockk.coEvery
import io.mockk.justRun
import io.mockk.mockk
import tech.figure.aggregate.common.Config
import tech.figure.aggregate.common.logger
import tech.figure.aggregate.common.models.UploadResult
import tech.figure.aggregate.repository.database.RavenDB
import tech.figure.aggregate.service.stream.consumers.EventStreamUploader
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.Logger
import tech.figure.aggregate.service.utils.installShutdownHook
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.BeforeAll
import tech.figure.aggregate.common.Environment
import tech.figure.aggregate.common.db.DBClient
import tech.figure.aggregate.service.flow.extensions.cancelOnSignal
import tech.figure.aggregate.service.test.utils.Defaults.blockData
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventStreamUploaderTests {

    val log: Logger = logger()
    val ravenClient = mockk<RavenDB>()

    val environment: Environment = Environment.local
    lateinit var config: Config

    val dbClient = mockk<DBClient>()
    val shutDownSignal: Channel<Unit> = installShutdownHook(log)

    @BeforeAll
    fun setup() {

        Database.connect(
            url = listOf(
                "jdbc:h2:mem:test",
                "DB_CLOSE_DELAY=-1",
                "LOCK_TIMEOUT=10000",
            ).joinToString(";") + ";",
            driver = "org.h2.Driver"
        )

        config = ConfigLoaderBuilder.default()
            .addSource(EnvironmentVariablesPropertySource(useUnderscoresAsSeparator = true, allowUppercaseNames = true))
            .addPreprocessor(PropsPreprocessor("/local.env.properties"))
            .addSource(PropertySource.resource("/application.yml"))
            .build()
            .loadConfigOrThrow()
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun testEventStreamUploaderSuccess() {
        val blockFlow = blockData()
        var complete = false
        runBlocking {
            coEvery {
                ravenClient.writeBlockCheckpoint(any())
            } answers {
                complete = true
            }
            var inspected1 = false

            justRun { dbClient.handleInsert(any(), any())}

            var uploadResults1: List<UploadResult> = mutableListOf()
            withTimeoutOrNull(Duration.seconds(4)) {
                var inspected1 = false

                    uploadResults1 = EventStreamUploader(
                        blockFlow,
                        dbClient,
                        ravenClient,
                        "tp",
                        Pair(config.badBlockRange[0], config.badBlockRange[1]),
                        config.msgFeeHeight
                    )
                        .addExtractor(config.upload.extractors)
                        .upload { inspected1 = true }
                        .cancelOnSignal(shutDownSignal)
                        .toList()
            }
            assert(uploadResults1 != null && uploadResults1.isNotEmpty()) { "Stream (1) failed to collect in time" }
            assert(uploadResults1.size == 4)
        }
    }
}
