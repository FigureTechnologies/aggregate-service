package tech.figure.augment.dsl

import org.junit.jupiter.api.Test
import tech.figure.augment.dsl.Module.BANK
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
                    column { "col1" }
                    column { "col2" }
                    filter {
                        left = "left"
                        right = "right"
                        operator = "="
                    }
                }
                rpcSource {
                    module = "bank"
                }
                loggingOutput {
                    column { "out1" }
                    column { "out2" }
                }
            }
        }

        assertEquals(
            Job(
                name = "name",
                cron = "cron",
                query = Query(
                    sources = listOf(
                        DbSource(
                            table = "table",
                            columns = listOf("col1", "col2"),
                            filter = DbFilter(left = "left", right = "right", operator = Operator.EQUAL),
                        ),
                        RpcSource(module = BANK, filter = null),
                    ),
                    transforms = emptyList(),
                    output = LoggingOutput(listOf("out1", "out2")),
                ),
            ),
            job,
        )
    }
}
