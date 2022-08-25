package tech.figure.aggregator.api.route

import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.openAPIGen
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.route
import tech.figure.aggregator.api.cache.CacheService
import tech.figure.aggregator.api.route.v1.feeRoute
import tech.figure.aggregator.api.route.v1.txRoute
import io.ktor.application.Application
import io.ktor.application.application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import tech.figure.aggregate.common.DBConfig
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Properties

private const val OPEN_API_JSON_PATH="/openapi.json"

fun Application.configureRouting(properties: Properties, dwUri: String, dbConfig: DBConfig, apiHost: String) {
    val cacheService = CacheService(properties, dwUri, dbConfig)
    install(ContentNegotiation) {
        jackson()
    }
    install(OpenAPIGen) {
        info {
            version = "1.0.0"
            title = "Aggregate-Service-API"
        }

        server(apiHost) {
            description = "Aggregator-API Server"
        }
    }

    routing {
        get(OPEN_API_JSON_PATH) {
            call.respond(application.openAPIGen.api)
        }

        get("/") {
            call.respondRedirect("/swagger-ui/index.html?url=$OPEN_API_JSON_PATH", true)
        }
    }

    apiRouting {
        route("v1") {
            txRoute(cacheService)
            feeRoute(cacheService)
        }
    }
}

fun String.toOffsetDateTime(): OffsetDateTime = OffsetDateTime.of(
    LocalDate.parse(this, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay(),
    ZoneOffset.UTC
)


