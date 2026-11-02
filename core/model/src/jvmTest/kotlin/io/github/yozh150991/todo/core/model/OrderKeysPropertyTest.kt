package io.github.yozh150991.todo.core.model

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Property-based check (spec §12.1, Kotest property): any sequence of inserts at random positions
 * keeps the keys valid, unique and in the order the list was built.
 */
class OrderKeysPropertyTest {

    @Test
    fun randomInsertsKeepListOrder(): Unit = runBlocking {
        checkAll(300, Arb.list(Arb.int(0..1_000), 1..200)) { positions ->
            val keys = mutableListOf<String>()
            for (raw in positions) {
                val at = raw % (keys.size + 1)
                val key = OrderKeys.between(keys.getOrNull(at - 1), keys.getOrNull(at))
                assertTrue(OrderKeys.isValid(key), "invalid key '$key'")
                keys.add(at, key)
            }
            assertTrue(keys.zipWithNext().all { (a, b) -> a < b }, "keys out of order: $keys")
        }
    }
}
