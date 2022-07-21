package com.provenance.aggregator.api.config

import io.provenance.aggregate.common.DbTypes

data class CacheConfig(
    val addr: String,
    val cacheName: String,
    val dbMaxConnections: Int,
    val dbType: DbTypes,
)
