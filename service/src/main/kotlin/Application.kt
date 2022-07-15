package com.provenance.aggregator.api

import com.provenance.aggregator.api.com.provenance.aggregator.api.config.DbConfig
import com.provenance.aggregator.api.route.configureRouting
import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.PropertySource
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.preprocessor.PropsPreprocessor
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.provenance.aggregate.common.Environment
import java.util.Properties


fun unwrapEnvOrError(variable: String): String = requireNotNull(System.getenv(variable)) { "Missing $variable" }

fun loadConfig(): DbConfig {
    val environment: Environment =
        runCatching { Environment.valueOf(System.getenv("ENVIRONMENT")) }
            .getOrElse {
                error("Not a valid environment: ${System.getenv("ENVIRONMENT")}")
            }

    return ConfigLoader.builder()
        .addEnvironmentSource(useUnderscoresAsSeparator = true, allowUppercaseNames = true)
        .apply {
            if (environment.isLocal()) {
                addPreprocessor(PropsPreprocessor("/local.env.properties"))
            }
        }
        .addPropertySource(PropertySource.resource("/application.yaml"))
        .build()
        .loadConfigOrThrow()
}

fun main() {

    val properties = Properties().apply {
        put("user", unwrapEnvOrError("DB_USER"))
        put("password", unwrapEnvOrError("DB_PASSWORD"))
        put("warehouse", unwrapEnvOrError("DB_WAREHOUSE"))
        put("db", unwrapEnvOrError("DB_DATABASE"))
        put("schema", unwrapEnvOrError("DB_SCHEMA"))
        put("networkTimeout", "30")
        put("queryTimeout", "30")
    }

    val dbUri = "jdbc:snowflake://${unwrapEnvOrError("DB_HOST")}.snowflakecomputing.com"

    embeddedServer(Netty, port=8081) {
       configureRouting(properties, dbUri, loadConfig())
    }.start(wait = true)
}



