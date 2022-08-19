package com.tech.figure.aggregator.api.snowflake

class QueryBuilder(
    val statement: String,
    val columns: List<String>,
    val table: String,
    val criteria: String
) {

    companion object {
        fun builder() = Builder()
    }

    class Builder {
        private var statement: String = ""
        private var columns: List<String> = mutableListOf()
        private var table: String = ""
        private var criteria: String = ""

        fun statement(value: String) = apply { statement = value }

        fun columns(value: List<String>) = apply { columns = value }

        fun table(value: String) = apply { table = value }

        fun criteria(value: String) = apply { criteria = value }

        fun build(): QueryBuilder {
            return QueryBuilder(
                statement,
                columns,
                table,
                criteria
            )
        }
    }
}
