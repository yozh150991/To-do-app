package io.github.yozh150991.todo.core.model

import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

/** spec §4.2 `OutboxOperation.status`. */
enum class OutboxStatus(val wire: String) {
    PENDING("pending"),
    ACKED("acked"),
    ABSORBED_BY_BOOTSTRAP("absorbed_by_bootstrap"),
    NEEDS_RESOLUTION("needs_resolution"),
    ;

    companion object {
        fun fromWire(value: String): OutboxStatus =
            entries.firstOrNull { it.wire == value } ?: error("Unknown outbox status: $value")
    }
}

/** spec §4.2 `LocalWorkspace.claim_status`. */
enum class ClaimStatus(val wire: String) {
    LOCAL("local"),
    PREPARING("preparing"),
    BOUND("bound"),
    NEEDS_RESOLUTION("needs_resolution"),
    ;

    companion object {
        fun fromWire(value: String): ClaimStatus =
            entries.firstOrNull { it.wire == value } ?: error("Unknown claim status: $value")
    }
}

/**
 * Typed operation names stored in `OutboxOperation.op_type`.
 * The wire strings are part of the future sync contract (spec §8.1) and must never be renamed;
 * new operations get new names.
 */
enum class OperationType(val wire: String, val entity: EntityType) {
    PROJECT_CREATE("project.create", EntityType.PROJECT),
    TASK_CREATE("task.create", EntityType.TASK),
    TASK_UPDATE("task.update", EntityType.TASK),
    TASK_COMPLETE("task.complete", EntityType.TASK),
    TASK_UNCOMPLETE("task.uncomplete", EntityType.TASK),
    TASK_DELETE("task.delete", EntityType.TASK),
    TASK_RESTORE("task.restore", EntityType.TASK),
    ;

    companion object {
        fun fromWire(value: String): OperationType =
            entries.firstOrNull { it.wire == value } ?: error("Unknown operation type: $value")
    }
}

/** Activity events written from phase Ф1a onward (spec §5.8). */
enum class ActivityEvent(val wire: String) {
    CREATED("created"),
    CONTENT_CHANGED("content_changed"),
    COMPLETED("completed"),
    UNCOMPLETED("uncompleted"),
    DELETED("deleted"),
    RESTORED("restored"),
    ;

    companion object {
        fun fromWire(value: String): ActivityEvent =
            entries.firstOrNull { it.wire == value } ?: error("Unknown activity event: $value")
    }
}

/**
 * Immutable record of one local mutation waiting for the server (spec §4.2, §8.1.1).
 * `localSeq` is monotonic per workspace and handed out inside the same transaction
 * as the entity change.
 */
data class OutboxOperation(
    val operationId: String,
    val localSeq: Long,
    val workspaceId: String,
    val type: OperationType,
    val entityId: String,
    val fieldPatch: JsonObject,
    val baseRevision: Long?,
    val deviceId: String,
    val createdAt: Instant,
    val status: OutboxStatus,
    val attempts: Int,
    val lastError: String?,
)

/** spec §4.2 `LocalWorkspace`: identity of the offline workspace before and after the cloud claim. */
data class LocalWorkspace(
    val workspaceId: String,
    val accountScope: String,
    val inboxId: String,
    val deviceId: String,
    val claimStatus: ClaimStatus,
    val boundUserId: String?,
    val claimOperationId: String?,
    val lastLocalSeq: Long,
    val createdAt: Instant,
) {
    companion object {
        fun localScope(workspaceId: String) = "local:$workspaceId"
    }
}

/** spec §4.2 `ExternalIdentity`: stable mapping from an import source ID to a local UUID. */
data class ExternalIdentity(
    val source: String,
    val entityType: EntityType,
    val sourceId: String,
    val localId: String,
    val sourceHash: String,
    val importVersion: Int,
    val lastSeenAt: Instant,
) {
    companion object {
        const val SOURCE_TODOIST = "todoist"
    }
}
