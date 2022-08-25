package tech.figure.aggregator.api.job

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.PropertySource
import com.sksamuel.hoplite.addEnvironmentSource
import tech.figure.aggregate.common.unwrapEnvOrError
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

    // Load transaction and fee daily - job
    addresses.addrs.map {
        val mapUrl = listOf(
            "https://${unwrapEnvOrError("API_HOST")}/v1/transaction/net/${it.key}?startDate=${OffsetDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)}" +
                "endDate=${OffsetDateTime.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)}&denom=nhash",
            "https://${unwrapEnvOrError("API_HOST")}/v1/fee/net/${it.key}?startDate=${OffsetDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)}" +
                    "endDate=${OffsetDateTime.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)}&denom=nhash"
        )

        mapUrl.map {
            val request = Request.Builder()
                .url(it)
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
}

