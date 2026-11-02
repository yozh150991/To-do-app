package io.github.yozh150991.todo.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/*
 * Schema v1 (spec v2.6.1 §4, phase Ф0).
 *
 * Storage conventions (docs/DATA_MODEL.md):
 * - IDs are client-generated UUID strings.
 * - Absolute moments (created_at, completed_at, ...) are UTC epoch milliseconds.
 * - Civil dates/times (due_date, due_time, deadline) are ISO-8601 text, never UTC instants.
 * - Enums are stored by their stable `wire` names from :core:model.
 * - One database file per local workspace; account isolation is by file, not by column.
 * - Only Room annotations are used for indexes so schema validation after migrations stays exact;
 *   "only one" rules use a nullable marker column under a UNIQUE index (NULLs never collide).
 */

@Entity(
    tableName = "local_workspace",
    indices = [Index(value = ["singleton"], unique = true)],
)
data class LocalWorkspaceEntity(
    @PrimaryKey @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "account_scope") val accountScope: String,
    @ColumnInfo(name = "inbox_id") val inboxId: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "claim_status") val claimStatus: String,
    @ColumnInfo(name = "bound_user_id") val boundUserId: String?,
    @ColumnInfo(name = "claim_operation_id") val claimOperationId: String?,
    @ColumnInfo(name = "backup_checkpoint") val backupCheckpoint: String?,
    /** Last `local_seq` handed to an outbox operation; only ever incremented inside a write transaction. */
    @ColumnInfo(name = "last_local_seq") val lastLocalSeq: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Always 1: the unique index allows exactly one workspace row per database file. */
    @ColumnInfo(name = "singleton") val singleton: Int = 1,
)

@Entity(
    tableName = "project",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
        ),
    ],
    indices = [
        Index(value = ["parent_id"]),
        Index(value = ["inbox_marker"], unique = true),
        Index(value = ["deleted_at"]),
    ],
)
data class ProjectEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "color") val color: String?,
    @ColumnInfo(name = "parent_id") val parentId: String?,
    @ColumnInfo(name = "child_order") val childOrder: String,
    @ColumnInfo(name = "is_inbox") val isInbox: Boolean,
    /** 1 for the system Inbox, NULL otherwise; UNIQUE index enforces a single Inbox (spec §4). */
    @ColumnInfo(name = "inbox_marker") val inboxMarker: Int?,
    @ColumnInfo(name = "view_mode") val viewMode: String,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    @ColumnInfo(name = "archived_at") val archivedAt: Long?,
    @ColumnInfo(name = "revision") val revision: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
)

@Entity(
    tableName = "task",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
        ),
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
        ),
    ],
    indices = [
        Index(value = ["project_id"]),
        Index(value = ["parent_id"]),
        Index(value = ["due_date"]),
        Index(value = ["deadline"]),
        Index(value = ["deleted_at"]),
    ],
)
data class TaskEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "project_id") val projectId: String,
    /** No FK until the `section` table arrives in phase Ф1b. */
    @ColumnInfo(name = "section_id") val sectionId: String?,
    @ColumnInfo(name = "parent_id") val parentId: String?,
    @ColumnInfo(name = "child_order") val childOrder: String,
    @ColumnInfo(name = "day_order") val dayOrder: String?,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "source_url") val sourceUrl: String?,
    @ColumnInfo(name = "priority") val priority: Int,
    @ColumnInfo(name = "due_date") val dueDate: String?,
    @ColumnInfo(name = "due_time") val dueTime: String?,
    @ColumnInfo(name = "due_tz") val dueTz: String?,
    @ColumnInfo(name = "deadline") val deadline: String?,
    @ColumnInfo(name = "rrule") val rrule: String?,
    @ColumnInfo(name = "recur_from_completion") val recurFromCompletion: Boolean,
    @ColumnInfo(name = "recurrence_end_reason") val recurrenceEndReason: String?,
    @ColumnInfo(name = "recurrence_dtstart_date") val recurrenceDtstartDate: String?,
    @ColumnInfo(name = "recurrence_dtstart_time") val recurrenceDtstartTime: String?,
    @ColumnInfo(name = "recurrence_zone_mode") val recurrenceZoneMode: String?,
    @ColumnInfo(name = "recurrence_zone_id") val recurrenceZoneId: String?,
    @ColumnInfo(name = "recurrence_interval_unit") val recurrenceIntervalUnit: String?,
    @ColumnInfo(name = "recurrence_interval_n") val recurrenceIntervalN: Int?,
    @ColumnInfo(name = "recurrence_policy_version") val recurrencePolicyVersion: Int?,
    @ColumnInfo(name = "duration_min") val durationMin: Int?,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "is_completable") val isCompletable: Boolean,
    @ColumnInfo(name = "revision") val revision: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
)

/** spec §4.2 `TaskActivity`: survives the task (no FK) so history outlives deletes. */
@Entity(
    tableName = "task_activity",
    indices = [Index(value = ["task_id"]), Index(value = ["operation_id"])],
)
data class TaskActivityEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "event_type") val eventType: String,
    /** JSON object `{field: {"old": .., "new": ..}}`. */
    @ColumnInfo(name = "field_changes") val fieldChanges: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "operation_id") val operationId: String?,
)

/** spec §4.2 `OutboxOperation`; payload is immutable until Ack or explicit resolution. */
@Entity(
    tableName = "outbox_operation",
    indices = [
        Index(value = ["workspace_id", "local_seq"], unique = true),
        Index(value = ["status", "local_seq"]),
        Index(value = ["entity", "entity_id"]),
    ],
)
data class OutboxOperationEntity(
    @PrimaryKey @ColumnInfo(name = "operation_id") val operationId: String,
    @ColumnInfo(name = "local_seq") val localSeq: Long,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "entity") val entity: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "op_type") val opType: String,
    /** JSON object with the typed field patch. */
    @ColumnInfo(name = "field_patch") val fieldPatch: String,
    @ColumnInfo(name = "base_revision") val baseRevision: Long?,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "attempts") val attempts: Int,
    @ColumnInfo(name = "last_error") val lastError: String?,
)

/** spec §4.2 `ExternalIdentity`: one row per imported source entity, unique per source. */
@Entity(
    tableName = "external_identity",
    primaryKeys = ["source", "entity_type", "source_id"],
    indices = [Index(value = ["entity_type", "local_id"])],
)
data class ExternalIdentityEntity(
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "local_id") val localId: String,
    @ColumnInfo(name = "source_hash") val sourceHash: String,
    @ColumnInfo(name = "import_version") val importVersion: Int,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long,
)
