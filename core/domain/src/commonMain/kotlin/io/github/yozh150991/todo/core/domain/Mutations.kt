package io.github.yozh150991.todo.core.domain

import io.github.yozh150991.todo.core.database.LocalStore
import io.github.yozh150991.todo.core.database.LocalStoreProvider
import io.github.yozh150991.todo.core.database.entity.LocalWorkspaceEntity
import io.github.yozh150991.todo.core.database.entity.TaskActivityEntity
import io.github.yozh150991.todo.core.database.toDomain
import io.github.yozh150991.todo.core.database.toEntity
import io.github.yozh150991.todo.core.model.ActivityEvent
import io.github.yozh150991.todo.core.model.ClaimStatus
import io.github.yozh150991.todo.core.model.IdGenerator
import io.github.yozh150991.todo.core.model.LocalWorkspace
import io.github.yozh150991.todo.core.model.OperationPayload
import io.github.yozh150991.todo.core.model.OperationType
import io.github.yozh150991.todo.core.model.OrderKeys
import io.github.yozh150991.todo.core.model.Project
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * What every repository shares: the workspace bootstrap and the write transaction that records a
 * mutation (spec §3.2). One instance per database; [TaskRepository] creates it and hands it to the
 * project and reminder repositories, so they share the workspace cache and the bootstrap lock.
 */
internal class Mutations(
    private val stores: LocalStoreProvider,
    private val clock: Clock,
    val ids: IdGenerator,
    /** Separate from [ids] so tests can force an outbox failure after the entity row was written. */
    private val operationIds: IdGenerator,
    /** Zone of floating due times (spec §4.0.1 п. 1, п. 7): injected, never read inside the domain. */
    val deviceZone: () -> TimeZone,
) {
    suspend fun store(): LocalStore = stores.get()

    private val bootstrapMutex = Mutex()
    private var cachedWorkspace: LocalWorkspace? = null

    /**
     * Returns the local workspace, creating it together with the system Inbox on first use.
     * Identity fields (workspace, device, Inbox IDs) never change before the cloud claim (Ф5a),
     * so the result is cached; `lastLocalSeq` in the cached copy is informational only.
     */
    suspend fun workspace(): LocalWorkspace = bootstrapMutex.withLock {
        cachedWorkspace ?: run {
            val store = store()
            if (store.workspaces.get() == null) {
                store.write { if (workspaces.get() == null) createWorkspace() }
            }
            checkNotNull(store.workspaces.get()) { "Workspace bootstrap failed" }.toDomain()
        }.also { cachedWorkspace = it }
    }

    /**
     * Runs [block] in one write transaction. Everything it records commits or rolls back together,
     * and every row it touches gets the same [Tx.now]: that shared moment is what later lets one
     * Undo restore exactly the rows a command binned or archived.
     */
    suspend fun <R> write(block: suspend Tx.() -> R): R {
        val ws = workspace()
        return store().write { Tx(this, ws, clock.now()).block() }
    }

    inner class Tx(val store: LocalStore, val ws: LocalWorkspace, val now: Instant) {
        val nowMillis: Long get() = now.toEpochMilliseconds()

        /**
         * Appends the outbox operation for one changed entity and, for tasks, its activity record.
         * The payload gets the version key ([OperationPayload]); [patch] holds only the fields.
         */
        suspend fun record(
            type: OperationType,
            entityId: String,
            patch: JsonObject,
            baseRevision: Long?,
            activity: Activity? = null,
        ) {
            val operationId = operationIds.newId()
            if (activity != null) {
                store.activities.insert(
                    TaskActivityEntity(
                        id = ids.newId(),
                        taskId = entityId,
                        eventType = activity.event.wire,
                        fieldChanges = activity.changes.toString(),
                        createdAt = nowMillis,
                        deviceId = ws.deviceId,
                        operationId = operationId,
                    ),
                )
            }
            store.enqueue(
                operationId = operationId,
                workspaceId = ws.workspaceId,
                type = type,
                entityId = entityId,
                fieldPatchJson = versioned(patch).toString(),
                baseRevision = baseRevision,
                deviceId = ws.deviceId,
                createdAtMillis = nowMillis,
            )
        }
    }

    private suspend fun LocalStore.createWorkspace(): LocalWorkspaceEntity {
        val now = clock.now()
        val workspaceId = ids.newId()
        val deviceId = ids.newId()
        val inbox = Project(
            id = ids.newId(),
            name = TaskRepository.INBOX_NAME,
            childOrder = OrderKeys.first(),
            isInbox = true,
            createdAt = now,
            updatedAt = now,
        )
        val workspace = LocalWorkspaceEntity(
            workspaceId = workspaceId,
            accountScope = LocalWorkspace.localScope(workspaceId),
            inboxId = inbox.id,
            deviceId = deviceId,
            claimStatus = ClaimStatus.LOCAL.wire,
            boundUserId = null,
            claimOperationId = null,
            backupCheckpoint = null,
            lastLocalSeq = 0,
            createdAt = now.toEpochMilliseconds(),
        )
        workspaces.insert(workspace)
        projects.insert(inbox.toEntity())
        enqueue(
            operationId = operationIds.newId(),
            workspaceId = workspaceId,
            type = OperationType.PROJECT_CREATE,
            entityId = inbox.id,
            fieldPatchJson = versioned(
                buildJsonObject {
                    put("id", inbox.id)
                    put("name", inbox.name)
                    put("is_inbox", true)
                    put("child_order", inbox.childOrder)
                    put("created_at", now.toString())
                },
            ).toString(),
            baseRevision = null,
            deviceId = deviceId,
            createdAtMillis = now.toEpochMilliseconds(),
        )
        return workspace
    }

}

/** The `task_activity` row of a task mutation: event type and `{field: {"old": .., "new": ..}}`. */
internal class Activity(val event: ActivityEvent, val changes: JsonObject)

private fun versioned(patch: JsonObject): JsonObject = buildJsonObject {
    put(OperationPayload.KEY, OperationPayload.VERSION)
    patch.forEach { (key, value) -> put(key, value) }
}

/**
 * Changed fields of one entity: the outbox patch gets the new values, the activity gets old and new.
 * Keys are column names; insertion order is kept, so payloads are stable for tests and the server.
 */
internal class FieldChanges {
    private val entries = LinkedHashMap<String, Pair<JsonElement, JsonElement>>()

    val isEmpty: Boolean get() = entries.isEmpty()
    val keys: Set<String> get() = entries.keys

    fun add(field: String, old: JsonElement, new: JsonElement) {
        entries[field] = old to new
    }

    /** Adds the field only when the value differs. */
    fun <T> diff(field: String, old: T, new: T, json: (T) -> JsonElement) {
        if (old != new) add(field, json(old), json(new))
    }

    fun patch(): JsonObject = buildJsonObject { entries.forEach { (k, v) -> put(k, v.second) } }

    fun activity(): JsonObject = buildJsonObject {
        entries.forEach { (k, v) ->
            put(
                k,
                buildJsonObject {
                    put("old", v.first)
                    put("new", v.second)
                },
            )
        }
    }
}

internal fun jsonOf(value: String?): JsonElement = JsonPrimitive(value)

internal fun jsonOf(value: Int?): JsonElement = JsonPrimitive(value)

internal fun jsonOf(value: Boolean?): JsonElement = JsonPrimitive(value)

internal fun jsonOfMillis(value: Long?): JsonElement = JsonPrimitive(value?.let { Instant.fromEpochMilliseconds(it).toString() })
