package tech.figure.aggregate.service.stream.extractors.csv

import org.jetbrains.exposed.sql.transactions.transaction
import tech.figure.aggregate.common.toHexString
import tech.figure.aggregate.common.utils.sha256
import tech.figure.aggregate.service.stream.extractors.Extractor

abstract class DBExtractor: Extractor {

    fun syncInsert(vararg values: Any) {
        val hash = computeHash(values)

        transaction {

        }
    }


    /**
     * Compute the hash of a given row, returning a hex-encoded string.
     */
    fun computeHash(vararg values: Any?): String = sha256(values.mapNotNull { it?.toString() }).toHexString()

}
