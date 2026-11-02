package io.github.yozh150991.todo.core.model

import kotlin.uuid.Uuid

/**
 * Source of client-generated UUIDs (spec §4: "UUID генерується клієнтом").
 * Injected everywhere IDs are created so tests can make IDs deterministic
 * or force collisions.
 */
fun interface IdGenerator {
    fun newId(): String
}

object RandomUuidGenerator : IdGenerator {
    override fun newId(): String = Uuid.random().toString()
}

/** Entity kinds as they appear in the outbox, activity log and external identities. */
enum class EntityType(val wire: String) {
    TASK("task"),
    PROJECT("project"),
    SECTION("section"),
    LABEL("label"),
    ;

    companion object {
        fun fromWire(value: String): EntityType =
            entries.firstOrNull { it.wire == value } ?: error("Unknown entity type: $value")
    }
}
