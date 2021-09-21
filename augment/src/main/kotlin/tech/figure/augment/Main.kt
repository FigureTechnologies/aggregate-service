package tech.figure.augment

import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import tech.figure.augment.dsl.Job

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger(object {}::class.java)
    val job = Json.decodeFromString(Job.serializer(), System.getenv("JOB_JSON"))

    log.info("Running job - \${job.name}")

    // TODO steps
    // add query interface and generate select statement and get back raw json response
    // fold into rpc call and get back response
    // perform transforms on the response
    // filter the output - need to add to DSL
    // implement output step
    job.query.sources
}
