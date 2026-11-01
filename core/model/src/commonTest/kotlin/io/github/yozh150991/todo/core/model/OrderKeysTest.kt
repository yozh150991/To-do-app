package io.github.yozh150991.todo.core.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OrderKeysTest {

    @Test
    fun knownValuesMatchReferenceImplementation() {
        assertEquals("a0", OrderKeys.first())
        assertEquals("a1", OrderKeys.after("a0"))
        assertEquals("Zz", OrderKeys.before("a0"))
        assertEquals("a0V", OrderKeys.between("a0", "a1"))
        assertEquals("a0G", OrderKeys.between("a0", "a0V"))
        assertEquals("ZzV", OrderKeys.between("Zz", "a0"))
    }

    @Test
    fun repeatedAppendsStayShort() {
        var key = OrderKeys.first()
        repeat(100_000) {
            val next = OrderKeys.after(key)
            assertTrue(next > key, "'$next' must sort after '$key'")
            key = next
        }
        assertTrue(key.length <= 5, "100k appends produced a key of length ${key.length}")
    }

    @Test
    fun repeatedPrependsStayShort() {
        var key = OrderKeys.first()
        repeat(100_000) {
            val next = OrderKeys.before(key)
            assertTrue(next < key, "'$next' must sort before '$key'")
            key = next
        }
        assertTrue(key.length <= 5, "100k prepends produced a key of length ${key.length}")
    }

    @Test
    fun randomInsertionsKeepStrictOrder() {
        val random = Random(42)
        val keys = mutableListOf(OrderKeys.first())
        repeat(5_000) {
            val key = when (random.nextInt(3)) {
                0 -> OrderKeys.after(keys.last())
                1 -> OrderKeys.before(keys.first())
                else -> if (keys.size < 2) {
                    OrderKeys.after(keys.last())
                } else {
                    val i = random.nextInt(keys.size - 1)
                    OrderKeys.between(keys[i], keys[i + 1])
                }
            }
            assertTrue(OrderKeys.isValid(key), "Generated invalid key '$key'")
            keys.add(key)
            keys.sort()
        }
        assertEquals(keys.size, keys.toSet().size, "Keys must be unique")
        keys.zipWithNext().forEach { (a, b) -> assertTrue(a < b) }
    }

    @Test
    fun rejectsInvertedOrMalformedBounds() {
        assertFailsWith<IllegalArgumentException> { OrderKeys.between("a1", "a0") }
        assertFailsWith<IllegalArgumentException> { OrderKeys.between("a0", "a0") }
        assertFailsWith<IllegalArgumentException> { OrderKeys.between("a00", null) }
        assertFailsWith<IllegalArgumentException> { OrderKeys.between("", null) }
        assertFailsWith<IllegalArgumentException> { OrderKeys.between("!", null) }
    }
}
