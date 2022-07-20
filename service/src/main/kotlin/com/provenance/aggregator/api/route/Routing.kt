package com.provenance.aggregator.api.route

import com.provenance.aggregator.api.com.provenance.aggregator.api.cache.CacheService
import com.provenance.aggregator.api.com.provenance.aggregator.api.cache.json
import com.provenance.aggregator.api.com.provenance.aggregator.api.config.CacheConfig
import com.provenance.aggregator.api.snowflake.SnowflakeJDBC
import io.ktor.http.HttpStatusCode.Companion
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

import java.util.Properties

fun Application.configureRouting(properties: Properties, dbUri: String, dbConfig: CacheConfig) {

    val cacheService = CacheService(SnowflakeJDBC(properties, dbUri), dbConfig)

    routing {
        get("/address/{addr?}") {
            val address = call.parameters["addr"].toString()
            val date = call.request.queryParameters["date"].toString()

            try {
                val queryDate = OffsetDateTime.of(
                    LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay(),
                    ZoneOffset.UTC
                )

                //check the cache table for the specific address requested
                val response = cacheService.getTx(address, queryDate)
                call.respond(response.statusCode, response.message)
            } catch (e: Exception) {
                call.respond(Companion.BadRequest, e.message!!.json())
            }
        }
    }
}


