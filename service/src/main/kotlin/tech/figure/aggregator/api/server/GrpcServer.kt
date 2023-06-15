package tech.figure.aggregator.api.server

import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.ktor.application.Application
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineEnvironment
import io.ktor.server.engine.BaseApplicationEngine
import io.ktor.server.engine.ConnectorType
import io.ktor.server.engine.EngineAPI
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.EngineConnectorConfig
import io.ktor.server.engine.EngineSSLConnectorBuilder
import io.ktor.server.engine.applicationEngineEnvironment
import org.slf4j.LoggerFactory
import tech.figure.aggregator.api.server.interceptor.ExceptionInterceptor
import tech.figure.aggregator.api.server.interceptor.RequestInterceptor
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.GlobalScope.coroutineContext
import tech.figure.aggregate.common.logger
import tech.figure.aggregator.api.service.TransferService
import java.security.KeyStore

object GrpcServer {

    fun embeddedServer(
        services: List<TransferService>,
        parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
        connector: EngineConnectorConfig = Connectors.default,
        developmentMode: Boolean,
        module: Application.() -> Unit = {}
    ) : GrpcEngine {
        val config: GRPCConfiguration.() -> Unit = {
            grpc {
                services.forEach(::addService)
                maxConcurrentCallsPerConnection(200)
            }
        }

        val environment = applicationEngineEnvironment {
            this.log = LoggerFactory.getLogger("ktor.application")
            this.parentCoroutineContext = coroutineContext + parentCoroutineContext
            this.modules += module
            this.connectors += connector
            this.developmentMode = developmentMode
        }

        val configuration = GRPCConfiguration().also { it.config() }.build()

        return GrpcEngine(environment, configuration)
    }
}

@OptIn(EngineAPI::class)
class GRPCConfiguration : BaseApplicationEngine.Configuration() {
    data class Settings(
        val serverBlocks: List<ServerBuilder<*>.() -> Unit> = emptyList(),
        val maxConcurrentCallsPerConnection: Int? = null,
        val onStarted: suspend () -> Unit = {},
    )

    private var settings = Settings()

    fun grpc(block: ServerBuilder<*>.() -> Unit) {
        settings = settings.copy(serverBlocks = settings.serverBlocks + block)
    }

    fun maxConcurrentCallsPerConnection(calls: Int) {
        settings = settings.copy(maxConcurrentCallsPerConnection = calls)
    }

    fun onStarted(block: suspend () -> Unit) {
        settings = settings.copy(onStarted = block)
    }

    fun build(): Settings {
        return settings
    }
}

object Connectors {
    val default = http("localhost", 7777)

    fun https(
        keyStore: KeyStore,
        keyAlias: String,
        keyStorePassword: () -> CharArray,
        privateKeyPassword: () -> CharArray,
        builder: EngineSSLConnectorBuilder.() -> Unit = {},
    ) =
        EngineSSLConnectorBuilder(keyStore, keyAlias, keyStorePassword, privateKeyPassword)
            .apply(builder).also { logger().info("Using an https connection") }

    fun http(host: String, port: Int) =
        EngineConnectorBuilder(ConnectorType.HTTP).also {
            logger().info("Setting http $host:$port ")
            it.host = host
            it.port = port
        }
}

@OptIn(EngineAPI::class)
class GrpcEngine(
    environment: ApplicationEngineEnvironment,
    private val settings: GRPCConfiguration.Settings,
) : BaseApplicationEngine(environment) {

    private lateinit var server: Server

    override val application: Application = environment.application
    override fun start(wait: Boolean): ApplicationEngine {
        logger().info("starting GRPC server")

        server =
            NettyServerBuilder.forPort(environment.connectors.first().port)
                .apply { settings.serverBlocks.forEach { it(this) } }
                .intercept(ExceptionInterceptor())
                .intercept(RequestInterceptor())
                .apply {
                    if(settings.maxConcurrentCallsPerConnection != null) {
                        maxConcurrentCallsPerConnection(settings.maxConcurrentCallsPerConnection)
                    }
                }
                .build()

        environment.start()
        server.start()
        if(wait) {
            server.awaitTermination()
        }

        return this
    }

    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        server.shutdownNow()
        server.awaitTermination(gracePeriodMillis, MILLISECONDS)
        environment.stop()
    }

}
