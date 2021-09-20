package tech.figure.augment.dsl

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DslTests {
    @Test
    fun testSimpleDslUsage() {
        val job = job {
            name = "name"
            cron = "cron"
            query {
                dbSource {
                    table = "table"
                    filter {
                        left = "left"
                        right = "right"
                        operator = "="
                    }
                }
                rpcSource {
                    module = "module"
                }
            }
        }

        assertEquals(
            Job(
                name = "name",
                cron = "cron",
                query = Query(
                    sources = listOf(
                        DbSource(table = "table", filter = Filter(left = "left", right = "right", operator = Operator.EQUAL)),
                        RpcSource(module = "module"),
                    ),
                    transforms = emptyList(),
                    output = LoggingOutput(),
                ),
            ),
            job,
        )
    }
}
