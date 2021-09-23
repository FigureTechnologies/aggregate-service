package io.provenance.aggregate.service.aws.s3

import java.time.OffsetDateTime

object Keys {
    /**
     * Given an OffsetDateTime, generate a key prefix of the form "YYYY/MM/DD/HH"
     */
    fun prefix(d: OffsetDateTime) = "${d.year}/${d.month.value}/${d.dayOfMonth}/${d.hour}"
}