package tech.figure.augment

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import tendermint.types.Types.Header as GrpcHeader
import tendermint.types.BlockOuterClass.Block as GrpcBlock

fun GrpcBlock.dateTime() = this.header.dateTime()

fun GrpcHeader.dateTime(): OffsetDateTime? =
    runCatching { OffsetDateTime.ofInstant(Instant.ofEpochSecond(this.time.seconds), ZoneOffset.UTC) }.getOrNull()
