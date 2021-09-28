package tech.figure.augment

import org.junit.jupiter.api.Test
import tech.figure.augment.db.sql
import tech.figure.augment.dsl.DbFilter
import tech.figure.augment.dsl.DbSource
import tech.figure.augment.dsl.Operator
import kotlin.test.assertEquals

class DbTests {
    @Test
    fun testNoFilter() {
        val source = DbSource(
            table = "_table_",
            columns = listOf("_a_", "_b_"),
            filter = null,
        )
        val (sql, params) = sql(source)

        assertEquals("SELECT \"_a_\", \"_b_\" FROM \"_table_\";", sql.value)
        assertEquals(0, params.value.size, "params is not null = $params")
    }

    @Test
    fun testFilter() {
        val source = DbSource(
            table = "_table_",
            columns = listOf("_a_", "_b_"),
            filter = DbFilter(left = "_left_", right = "_right_", operator = Operator.EQUAL),
        )
        val (sql, params) = sql(source)

        assertEquals("SELECT \"_a_\", \"_b_\" FROM \"_table_\" WHERE \"_left_\" = ?;", sql.value)
        assertEquals(listOf("_right_"), params.value)
    }
}
