//package tech.figure.augment
//
//import org.junit.jupiter.api.Test
//import tech.figure.augment.dsl.LoggingOutput
//import kotlin.test.assertEquals
//
//class OutputTests {
//    @Test
//    fun testSubsetFilter() {
//        val data = listOf(
//            mapOf("a" to "1", "b" to "2", "c" to "3"),
//            mapOf("a" to "1", "b" to "2", "c" to "3"),
//            mapOf("a" to "1", "b" to "2", "c" to "3"),
//        )
//        val expected = listOf(
//            mapOf("a" to "1"),
//            mapOf("a" to "1"),
//            mapOf("a" to "1"),
//        )
//
//        assertEquals(expected, data.filterColumns(LoggingOutput(listOf("a"))))
//    }
//
//    @Test
//    fun testSpanningFilter() {
//        val data = listOf(
//            mapOf("a" to "1", "b" to "2", "c" to "3"),
//            mapOf("a" to "1", "b" to "2", "c" to "3"),
//            mapOf("a" to "1", "b" to "2", "c" to "3"),
//        )
//        val expected = listOf(
//            mapOf("a" to "1"),
//            mapOf("a" to "1"),
//            mapOf("a" to "1"),
//        )
//
//        assertEquals(expected, data.filterColumns(LoggingOutput(listOf("a", "d", "e"))))
//    }
//
//    @Test
//    fun testNoFilter() {
//        val data = listOf(
//            mapOf("a" to "1", "b" to "2", "c" to "3"),
//            mapOf("a" to "1", "b" to "2", "c" to "3"),
//            mapOf("a" to "1", "b" to "2", "c" to "3"),
//        )
//        val expected = listOf<Map<String, String>>(
//            emptyMap(),
//            emptyMap(),
//            emptyMap(),
//        )
//
//        assertEquals(expected, data.filterColumns(LoggingOutput(emptyList())))
//    }
//
//    @Test
//    fun testDisjointFilter() {
//        val data = listOf(
//            mapOf("a" to "1", "b" to "2", "c" to "3"),
//            mapOf("a" to "1", "b" to "2", "c" to "3"),
//            mapOf("a" to "1", "b" to "2", "c" to "3"),
//        )
//        val expected = listOf<Map<String, String>>(
//            emptyMap(),
//            emptyMap(),
//            emptyMap(),
//        )
//
//        assertEquals(expected, data.filterColumns(LoggingOutput(listOf("d", "e"))))
//    }
//}
