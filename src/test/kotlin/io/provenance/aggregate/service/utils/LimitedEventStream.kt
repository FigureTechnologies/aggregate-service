package io.provenance.aggregate.service.utils

import arrow.core.Either
import io.provenance.aggregate.service.stream.EventStream
import io.provenance.aggregate.service.stream.IEventStream
import io.provenance.aggregate.service.stream.StreamBlock
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.take

class LimitedEventStream(private val eventStream: EventStream, private val takeCount: Int) :
    IEventStream by eventStream {
    @OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override suspend fun streamBlocks(
        fromHeight: Long?,
        concurrency: Int
    ): Flow<Either<Throwable, StreamBlock>> = eventStream.streamBlocks(fromHeight, concurrency).take(takeCount)
}