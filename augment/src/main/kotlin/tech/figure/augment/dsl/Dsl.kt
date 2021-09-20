package tech.figure.augment.dsl

// name: nycb_account_balance
// cron: "<cron schedule>"
// type: grpc
// query:
// select: balance
// from: bank
// where: account.tag = "nycb.passport.pb"

// source:
//   db:
//   table: attributes
//   filter: value = ""
//   rpc:
//   module: bank
//   denom: denom

// TODO validate function on all builder classes?

data class Job(
    val name: String,
    val cron: String,
    val query: Query,
)

class JobBuilder {
    var name: String = ""
    var cron: String = ""
    var query: Query = Query(emptyList(), emptyList(), LoggingOutput())

    fun query(block: QueryBuilder.() -> Unit) {
        query = QueryBuilder().apply(block).build()
    }

    fun build(): Job = Job(name, cron, query)
}

fun job(block: JobBuilder.() -> Unit): Job = JobBuilder().apply(block).build()

data class Query(
    val sources: List<Source>,
    val transforms: List<Transform>,
    val output: Output,
)

class QueryBuilder {
    private val sources = mutableListOf<Source>()
    private val transforms = mutableListOf<Transform>()
    private var output: Output = LoggingOutput()

    fun dbSource(block: DbSourceBuilder.() -> Unit) {
        sources.add(DbSourceBuilder().apply(block).build())
    }
    fun rpcSource(block: RpcSourceBuilder.() -> Unit) {
        sources.add(RpcSourceBuilder().apply(block).build())
    }

    fun s3Output(block: S3OutputBuilder.() -> Unit) {
        output = S3OutputBuilder().apply(block).build()
    }

    fun build(): Query = Query(sources, transforms, output)
}

sealed class Source
class DbSource(val table: String, val filter: Filter?) : Source() {
    override fun equals(other: Any?): Boolean {
        return if (other?.javaClass == javaClass) {
            val o = (other as DbSource)

            o.table == table && (o.filter?.equals(filter) == true || (o.filter == null && filter == null))
        } else {
            false
        }
    }
}
class RpcSource(val module: String) : Source() { // TODO change to something besides String
    override fun equals(other: Any?): Boolean {
        return if (other?.javaClass == javaClass) {
            val o = (other as RpcSource)

            o.module == module
        } else {
            false
        }
    }
}

class DbSourceBuilder {
    var table: String = ""
    private var filter: Filter? = null

    fun filter(block: FilterBuilder.() -> Unit) {
        filter = FilterBuilder().apply(block).build()
    }

    fun build(): DbSource = DbSource(table, filter)
}

class RpcSourceBuilder {
    var module: String = ""

    fun build(): RpcSource = RpcSource(module)
}

data class Filter(
    val left: String,
    val right: String,
    val operator: Operator, // TODO change to enum
)

enum class Operator {
    EQUAL,
}

class FilterBuilder {
    var left: String = ""
    var right: String = ""
    var operator: String = ""

    fun build(): Filter {
        val operator = when (operator) {
            "=" -> Operator.EQUAL
            else -> throw IllegalStateException("The only operator(s) currently supported is \"=\"")
        }

        return Filter(left, right, operator)
    }
}

sealed class Transform

sealed class Output
class LoggingOutput : Output() {
    override fun equals(other: Any?): Boolean {
        return other?.javaClass == javaClass
    }
}
class S3Output : Output() {
    override fun equals(other: Any?): Boolean {
        return other?.javaClass == javaClass
    }
}

class S3OutputBuilder {
    fun build(): S3Output = S3Output()
}
