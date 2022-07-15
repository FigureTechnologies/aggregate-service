package com.provenance.aggregator.api.com.provenance.aggregator.api.config

import io.provenance.aggregate.common.DbTypes

data class DbConfig(
    val addr: String,
    val dbName: String,
    val dbMaxConnections: Int,
    val dbType: DbTypes,
)
