package io.github.yozh150991.todo.core.database

import androidx.room3.*
import io.github.yozh150991.todo.core.database.entity.OutboxOperationEntity
import io.github.yozh150991.todo.core.model.OperationType
import io.github.yozh150991.todo.core.model.OutboxStatus

/**
 * The only way the domain layer touches Room (spec §3.2).
 *
 * [write] runs a block in one SQLite write transaction: the entity change, its activity record and
 * its outbox operation either all commit or all roll back (invariant §2.1.1). Any exception thrown
 * inside the block aborts the whole transaction.
 */
class LocalStore(val database: AppDatabase) {

    val workspaces get() = database.workspaceDao()
    val projects get() = database.projectDao()
    val tasks get() = database.taskDao()
    val activities get() = database.taskActivityDao()
    val outbox get() = database.outboxDao()
    val externalIdentities get() = database.externalIdentityDao()
    val sections get() = database.sectionDao()
    val labels get() = database.labelDao()

    suspend fun <R> write(block: suspend LocalStore.() -> R): R =
        database.withWriteTransaction { this@LocalStore.block() }

    /**
     * Appends one pending operation with the next `local_seq` of [workspaceId].
     * Must be called inside [write]; the sequence bump and the insert share the transaction,
     * so a rollback never leaves a gap that a later operation could reuse out of order.
     */
    suspend fun enqueue(
        operationId: String,
        workspaceId: String,
        type: OperationType,
        entityId: String,
        fieldPatchJson: String,
        baseRevision: Long?,
        deviceId: String,
        createdAtMillis: Long,
    ): OutboxOperationEntity {
        val updated = workspaces.incrementLocalSeq(workspaceId)
        check(updated == 1) { "Workspace $workspaceId not found while enqueuing $type" }
        val entity = OutboxOperationEntity(
            operationId = operationId,
            localSeq = workspaces.lastLocalSeq(workspaceId),
            workspaceId = workspaceId,
            entity = type.entity.wire,
            entityId = entityId,
            opType = type.wire,
            fieldPatch = fieldPatchJson,
            baseRevision = baseRevision,
            deviceId = deviceId,
            createdAt = createdAtMillis,
            status = OutboxStatus.PENDING.wire,
            attempts = 0,
            lastError = null,
        )
        outbox.insert(entity)
        return entity
    }

    fun close() = database.close()
}
