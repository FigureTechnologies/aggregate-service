package io.provenance.aggregate.service.aws.s3

import software.amazon.awssdk.core.async.AsyncRequestBody

interface StreamableObject {
    val key: String
    val body: AsyncRequestBody
}