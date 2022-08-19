package com.tech.figure.aggregator.api.route

import com.papsign.ktor.openapigen.APITag

enum class Tag(override val description: String): APITag {
    Transaction("Aggregated total transaction of an address as per date range."),
    Fee("Aggregated total fee of an address as per date range.")
}
