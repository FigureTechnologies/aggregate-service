package io.provenance.aggregate.common.aws.s3

import java.time.OffsetDateTime

/**
 * A value class wrapper around a string that represent the key of an object on S3.
 *
 * @property value The S3 key to wrap.
 */
@JvmInline
value class S3Key(val value: String) {
    companion object {
        /**
         * Given an OffsetDateTime, generate a key prefix of the form "YYYY/MM/DD/HH"
         */
        fun createPrefix(d: OffsetDateTime) = "${d.year}/${d.month.value}/${d.dayOfMonth}/${d.hour}"

        fun create(date: OffsetDateTime, vararg parts: String): S3Key =
            S3Key("${createPrefix(date)}/${parts.joinToString("/")}")
    }

    override fun toString(): String = value
}
