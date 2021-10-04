package io.provenance.aggregate.service.aws.s3

import java.time.OffsetDateTime

@JvmInline
value class S3Key(val value: String) {
    companion object {
        /**
         * Given an OffsetDateTime, generate a key prefix of the form "YYYY/MM/DD/HH"
         */
        fun createPrefix(d: OffsetDateTime) = "${d.year}/${d.month.value}/${d.dayOfMonth}/${d.hour}"
    }
}