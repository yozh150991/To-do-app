package io.github.yozh150991.todo.core.domain

import io.github.yozh150991.todo.core.database.LocalStore
import io.github.yozh150991.todo.core.database.createAppDatabase
import io.github.yozh150991.todo.core.database.entity.OutboxOperationEntity
import io.github.yozh150991.todo.core.database.entity.ProjectEntity
import io.github.yozh150991.todo.core.database.entity.TaskEntity
import io.github.yozh150991.todo.core.model.IdGenerator
import io.github.yozh150991.todo.core.model.RandomUuidGenerator
import io.github.yozh150991.todo.core.model.TimeZones
import io.github.yozh150991.todo.core.model.ValidationCode
import io.github.yozh150991.todo.core.model.ValidationException
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** A clock that moves one millisecond per reading, so separate commands never share a moment. */
class TickingClock(private var next: Instant = Instant.parse("2026-11-21T10:00:00Z")) : Clock {
    override fun now(): Instant = next.also { next += 1.milliseconds }
}

/** Unique operation IDs until [arm] is called; then the second ID repeats the first and the outbox insert fails. */
class ArmableOperationIds : IdGenerator {
    private var armed = false
    private var first: String? = null

    fun arm() {
        armed = true
        first = null
    }

    override fun newId(): String {
        if (!armed) return RandomUuidGenerator.newId()
        val id = first ?: RandomUuidGenerator.newId().also { first = it }
        return id
    }
}

/** One temporary database file and a repository on it; [close] after the test. */
class RepoFixture(
    val zone: TimeZone = TimeZones.of("Europe/Warsaw"),
    operationIds: IdGenerator = RandomUuidGenerator,
) {
    private val file = File(Files.createTempDirectory("repo").toFile(), "todo.db")
    val store = LocalStore(createAppDatabase(file))
    val repo = TaskRepository(store, clock = TickingClock(), operationIds = operationIds, deviceZone = { zone })

    suspend fun ops(): List<OutboxOperationEntity> = store.outbox.all()

    suspend fun opsFor(entityId: String): List<OutboxOperationEntity> = store.outbox.all().filter { it.entityId == entityId }

    suspend fun task(id: String): TaskEntity = checkNotNull(store.tasks.getById(id)) { "no task $id" }

    suspend fun project(id: String): ProjectEntity = checkNotNull(store.projects.getById(id)) { "no project $id" }

    /** Everything a failed command must leave untouched. */
    suspend fun snapshot(): Triple<List<TaskEntity>, List<ProjectEntity>, Int> =
        Triple(store.tasks.all(), store.projects.all(), store.outbox.count())

    fun close() = store.close()
}

fun OutboxOperationEntity.patch(): JsonObject = Json.parseToJsonElement(fieldPatch).jsonObject

/** Runs [block], expects a [ValidationException] with [code], and checks the database did not change. */
suspend fun RepoFixture.rejects(code: ValidationCode, block: suspend () -> Unit) {
    val before = snapshot()
    val e = assertFailsWith<ValidationException> { block() }
    assertEquals(code, e.code, "unexpected code for: ${e.message}")
    assertEquals(before, snapshot(), "a rejected command must not write anything")
}
