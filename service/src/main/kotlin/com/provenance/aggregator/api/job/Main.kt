package com.provenance.aggregator.api.job

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.PropertySource
import com.sksamuel.hoplite.addEnvironmentSource
import io.provenance.aggregate.common.extensions.unwrapEnvOrError
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import org.slf4j.LoggerFactory

fun loadJobConfig(): JobConfig {
    return ConfigLoader.builder()
        .addEnvironmentSource(useUnderscoresAsSeparator = true, allowUppercaseNames = true)
        .addPropertySource(PropertySource.resource("/address.yaml"))
        .build()
        .loadConfigOrThrow()
}

fun main() {
    val log = LoggerFactory.getLogger("main")
    val addresses = loadJobConfig()
    val client = OkHttpClient()

    addresses.addrs.map {
        val url = "https://${unwrapEnvOrError("API_HOST")}/address/${it.key}?date=${OffsetDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)}" +
                "&denom=nhash"

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object: Callback {
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use{
                        log.info(response.body?.string())
                    }
                }
            }
        )
    }
}

