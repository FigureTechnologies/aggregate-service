package io.provenance.aggregate.service.utils

import io.provenance.aggregate.service.stream.EventStream

/***********************************************************************************************************************
 * Streaming
 **********************************************************************************************************************/

const val MIN_BLOCK_HEIGHT: Long = 2270370
const val MAX_BLOCK_HEIGHT: Long = 2270469

const val EXPECTED_TOTAL_BLOCKS: Long = (MAX_BLOCK_HEIGHT - MIN_BLOCK_HEIGHT) + 1
const val EXPECTED_NONEMPTY_BLOCKS: Long = 29
const val EXPECTED_EMPTY_BLOCKS: Long = EXPECTED_TOTAL_BLOCKS - EXPECTED_NONEMPTY_BLOCKS

const val BATCH_SIZE: Int = 4

val heights: List<Long> = (MIN_BLOCK_HEIGHT..MAX_BLOCK_HEIGHT).toList()

val heightChunks: List<Pair<Long, Long>> = heights
    .chunked(EventStream.TENDERMINT_MAX_QUERY_RANGE)
    .map { Pair(it.minOrNull()!!, it.maxOrNull()!!) }

/***********************************************************************************************************************
 * AWS
 **********************************************************************************************************************/

const val S3_REGION: String = "us-east-1"
const val S3_BUCKET: String = "test-bucket"