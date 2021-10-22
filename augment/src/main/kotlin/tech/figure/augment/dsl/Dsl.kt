package tech.figure.augment.dsl

import kotlinx.serialization.Serializable

// TODO validate function on all builder classes?

@Serializable
data class Job(
    val name: String,
    val cron: String,
    val query: Query,
)

class JobBuilder {
    var name: String = ""
    var cron: String = ""
    var query: Query = Query(emptyList(), emptyList(), LoggingOutput(emptyList()))

    fun query(block: QueryBuilder.() -> Unit) {
        query = QueryBuilder().apply(block).build()
    }

    fun build(): Job = Job(name, cron, query)
}

fun job(block: JobBuilder.() -> Unit): Job = JobBuilder().apply(block).build()

@Serializable
data class Query(
    val sources: List<Source>,
    val transforms: List<Transform>,
    val output: Output,
)

class QueryBuilder {
    private val sources = mutableListOf<Source>()
    private val transforms = mutableListOf<Transform>()
    private var output: Output = LoggingOutput(emptyList())

    fun dbSource(block: DbSourceBuilder.() -> Unit) {
        sources.add(DbSourceBuilder().apply(block).build())
    }
    fun rpcSource(block: RpcSourceBuilder.() -> Unit) {
        sources.add(RpcSourceBuilder().apply(block).build())
    }

    fun loggingOutput(block: LoggingOutputBuilder.() -> Unit) {
        output = LoggingOutputBuilder().apply(block).build()
    }

    fun s3Output(block: S3OutputBuilder.() -> Unit) {
        output = S3OutputBuilder().apply(block).build()
    }

    fun build(): Query = Query(sources, transforms, output)
}

@Serializable
sealed class Source
@Serializable
class DbSource(val columns: List<String>, val table: String, val filter: DbFilter?) : Source() {
    override fun equals(other: Any?): Boolean {
        return if (other?.javaClass == javaClass) {
            val o = (other as DbSource)

            o.table == table && (o.filter?.equals(filter) == true || (o.filter == null && filter == null))
                && columns.all { o.columns.contains(it) } && columns.size == o.columns.size
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        var result = columns.hashCode()
        result = 31 * result + table.hashCode()
        result = 31 * result + (filter?.hashCode() ?: 0)
        return result
    }
}
@Serializable
class RpcSource(val module: Module, val filter: RpcFilter?) : Source() {
    override fun equals(other: Any?): Boolean {
        return if (other?.javaClass == javaClass) {
            val o = (other as RpcSource)

            o.module == module && (o.filter?.equals(filter) == true || (o.filter == null && filter == null))
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        var result = module.hashCode()
        result = 31 * result + (filter?.hashCode() ?: 0)
        return result
    }
}

class DbSourceBuilder {
    var table: String = ""
    private var filter: DbFilter? = null
    private val columns = mutableListOf<String>()

    fun column(block: () -> String) {
        columns.add(block())
    }

    fun filter(block: DbFilterBuilder.() -> Unit) {
        filter = DbFilterBuilder().apply(block).build()
    }

    fun build(): DbSource = DbSource(columns, table, filter)
}

class RpcSourceBuilder {
    var module: String = ""
    private var filter: RpcFilter? = null

    fun filter(block: RpcFilterBuilder.() -> Unit) {
        filter = RpcFilterBuilder().apply(block).build()
    }

    fun build(): RpcSource {
        val module = when (module) {
            "bank" -> Module.BANK
            else -> throw IllegalStateException("The only module(s) currently supported is \"bank\"")
        }

        return RpcSource(module, filter)
    }
}

@Serializable
enum class Module {
    BANK,
}

@Serializable
data class DbFilter(
    val left: String,
    val right: String,
    val operator: Operator,
)

@Serializable
data class RpcFilter(
    val setter: String,
    val value: String,
)

@Serializable
enum class Operator {
    EQUAL,
}

class DbFilterBuilder {
    var left: String = ""
    var right: String = ""
    var operator: String = ""

    fun build(): DbFilter {
        val operator = when (operator) {
            "=" -> Operator.EQUAL
            else -> throw IllegalStateException("The only operator(s) currently supported is \"=\"")
        }

        return DbFilter(left, right, operator)
    }
}

class RpcFilterBuilder {
    var setter: String = ""
    var value: String = ""

    fun build(): RpcFilter {
        return RpcFilter(setter, value)
    }
}

@Serializable
sealed class Transform

@Serializable
sealed class Output
@Serializable
class LoggingOutput(val columns: List<String>) : Output() {
    override fun equals(other: Any?): Boolean {
        return if (other?.javaClass == javaClass) {
            val o = (other as LoggingOutput)

            return columns.all { o.columns.contains(it) } && columns.size == o.columns.size
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        return columns.hashCode()
    }
}
@Serializable
class S3Output(val bucket: String, val tableName: String, val columns: List<String>) : Output() {
    override fun equals(other: Any?): Boolean {
        return if (other?.javaClass == javaClass) {
            val o = (other as S3Output)

            return bucket == o.bucket && tableName == o.tableName && columns.all { o.columns.contains(it) } && columns.size == o.columns.size
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        var result = bucket.hashCode()
        result = 31 * result + tableName.hashCode()
        result = 31 * result + columns.hashCode()
        return result
    }
}

class LoggingOutputBuilder {
    private val columns = mutableListOf<String>()

    fun column(block: () -> String) {
        columns.add(block())
    }

    fun build(): LoggingOutput = LoggingOutput(columns)
}

class S3OutputBuilder {
    var bucket: String = ""
    var tableName: String = ""
    private val columns = mutableListOf<String>()

    fun column(block: () -> String) {
        columns.add(block())
    }

    fun build(): S3Output = S3Output(bucket, tableName, columns)
}

typealias Row = Map<String, String>
typealias Data = List<Row>
fun emptyData(): Data = listOf()
