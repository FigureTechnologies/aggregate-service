package com.tech.figure.aggregator.api.config

import tech.figure.aggregate.common.DbTypes

data class CacheConfig(
    val addr: String,
    val cacheName: String,
    val dbMaxConnections: Int,
    val dbType: DbTypes,
)
