package io.provenance.aggregateservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AggregateServiceApplication

fun main(args: Array<String>) {
    runApplication<AggregateServiceApplication>(*args)
}
