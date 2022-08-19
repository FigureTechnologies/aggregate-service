package tech.figure.aggregate.service.test.utils


import io.provenance.eventstream.stream.EventStream
import kotlinx.coroutines.ExperimentalCoroutinesApi

/***********************************************************************************************************************
 * Streaming
 **********************************************************************************************************************/

/**
 * Minimum historical block height from block templates in `resources/templates/block`
 */
const val MIN_HISTORICAL_BLOCK_HEIGHT: Long = 2270370

/**
 * Maximum historical block height from block templates in `resources/templates/block`
 */
const val MAX_HISTORICAL_BLOCK_HEIGHT: Long = 2270466

/**
 * Minimum live block height from block templates in `resources/templates/live`
 */
const val MIN_LIVE_BLOCK_HEIGHT: Long = 3126935

/**
 * Maximum live block height from block templates in `resources/templates/live`
 */
const val MAX_LIVE_BLOCK_HEIGHT: Long = 3126943

const val EXPECTED_TOTAL_BLOCKS: Long = (MAX_HISTORICAL_BLOCK_HEIGHT - MIN_HISTORICAL_BLOCK_HEIGHT) + 1
const val EXPECTED_NONEMPTY_BLOCKS: Long = 29
const val EXPECTED_EMPTY_BLOCKS: Long = EXPECTED_TOTAL_BLOCKS - EXPECTED_NONEMPTY_BLOCKS

const val BATCH_SIZE: Int = 4

val heights: List<Long> = (MIN_HISTORICAL_BLOCK_HEIGHT..MAX_HISTORICAL_BLOCK_HEIGHT).toList() + (MIN_LIVE_BLOCK_HEIGHT..MAX_LIVE_BLOCK_HEIGHT).toList()

@OptIn(ExperimentalCoroutinesApi::class)
val heightChunks: List<Pair<Long, Long>> = heights
    .chunked(EventStream.TENDERMINT_MAX_QUERY_RANGE)
    .map { Pair(it.minOrNull()!!, it.maxOrNull()!!) }

/***********************************************************************************************************************
 * AWS
 **********************************************************************************************************************/

const val S3_REGION: String = "us-east-1"
const val S3_BUCKET: String = "test-bucket"
const val DYNAMODB_BLOCK_BATCH_TABLE: String = "test-Aggregate-Service-Block-Batch"
const val DYNAMODB_BLOCK_METADATA_TABLE: String = "test-Aggregate-Service-Block-Metadata"
const val DYNAMODB_SERVICE_METADATA_TABLE: String = "test-Aggregate-Service-Metadata"
