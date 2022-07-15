package com.provenance.aggregator.api.com.provenance.aggregator.api.model

import io.ktor.http.HttpStatusCode

data class Response (
    val message: String,
    val statusCode: HttpStatusCode
)
