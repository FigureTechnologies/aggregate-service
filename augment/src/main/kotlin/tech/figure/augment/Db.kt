package tech.figure.augment.db

import tech.figure.augment.dsl.DbSource
import tech.figure.augment.dsl.Operator

fun String.escape() = "\"$this\""

data class Sql(val value: String)
data class SqlParams(val value: List<String>)

fun String.toSql() = Sql(this)

fun Operator.toSql() = when (this) {
    Operator.EQUAL -> "="
}

fun sql(source: DbSource): Pair<Sql, SqlParams> {
    val columns = source.columns.joinToString(separator = ", ") { it.escape() }
    val filter = source.filter?.let { "${it.left.escape()} ${it.operator.toSql()} ?" }
    val params = SqlParams(listOfNotNull(source.filter?.right))

    return "SELECT $columns FROM ${source.table.escape()}${ if (filter != null) " WHERE $filter;" else ";" }".toSql() to params
}
