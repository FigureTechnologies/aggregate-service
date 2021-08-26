package io.provenance.aggregate.service.stream.extensions

import io.provenance.aggregate.service.stream.models.ABCIInfoResponse
import io.provenance.aggregate.service.stream.models.ABCIInfoResponseResult
import io.provenance.aggregate.service.stream.models.ABCIInfoResponseResultResponse

fun ABCIInfoResponse.Companion.withHeightAndHash(height: Long?, appHash: String?) =
    ABCIInfoResponse(
        id = -1,
        jsonrpc = "2.0",
        result = ABCIInfoResponseResult(
            response = ABCIInfoResponseResultResponse(
                data = "provenanced",
                lastBlockAppHash = appHash,
                lastBlockHeight = height
            )
        )
    )

fun ABCIInfoResponse.Companion.withHeight(height: Long?) = withHeightAndHash(height, null)
