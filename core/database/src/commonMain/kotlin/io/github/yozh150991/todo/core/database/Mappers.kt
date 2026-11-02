package io.github.yozh150991.todo.core.database

import io.github.yozh150991.todo.core.database.entity.ExternalIdentityEntity
import io.github.yozh150991.todo.core.database.entity.LocalWorkspaceEntity
import io.github.yozh150991.todo.core.database.entity.OutboxOperationEntity
import io.github.yozh150991.todo.core.database.entity.ProjectEntity
import io.github.yozh150991.todo.core.database.entity.TaskEntity
import io.github.yozh150991.todo.core.model.ClaimStatus
import io.github.yozh150991.todo.core.model.Due
import io.github.yozh150991.todo.core.model.EntityType
import io.github.yozh150991.todo.core.model.ExternalIdentity
import io.github.yozh150991.todo.core.model.LocalWorkspace
import io.github.yozh150991.todo.core.model.OperationType
import io.github.yozh150991.todo.core.model.OutboxOperation
import io.github.yozh150991.todo.core.model.OutboxStatus
import io.github.yozh150991.todo.core.model.Priority
import io.github.yozh150991.todo.core.model.Project
import io.github.yozh150991.todo.core.model.RecurrenceEndReason
import io.github.yozh150991.todo.core.model.Task
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.time.Instant

private fun Long.toInstant(): Instant = Instant.fromEpochMilliseconds(this)

fun TaskEntity.toDomain(): Task = Task(
    id = id,
    projectId = projectId,
    sectionId = sectionId,
    parentId = parentId,
    childOrder = childOrder,
    content = content,
    description = description,
    sourceUrl = sourceUrl,
    priority = Priority.fromLevel(priority),
    due = dueDate?.let { date ->
        Due(
            date = LocalDate.parse(date),
            time = dueTime?.let(LocalTime::parse),
            zoneId = dueTz,
        )
    },
    deadline = deadline?.let(LocalDate::parse),
    durationMinutes = durationMin,
    isCompletable = isCompletable,
    completedAt = completedAt?.toInstant(),
    recurrenceEndReason = RecurrenceEndReason.fromWire(recurrenceEndReason),
    revision = revision,
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant(),
    deletedAt = deletedAt?.toInstant(),
)

/**
 * Converts a domain task to a row. Recurrence columns are not modelled in the domain before Ф2,
 * so they are copied from [existing] to avoid silently erasing data written by a newer component.
 */
fun Task.toEntity(existing: TaskEntity? = null): TaskEntity = TaskEntity(
    id = id,
    projectId = projectId,
    sectionId = sectionId,
    parentId = parentId,
    childOrder = childOrder,
    dayOrder = existing?.dayOrder,
    content = content,
    description = description,
    sourceUrl = sourceUrl,
    priority = priority.level,
    dueDate = due?.date?.toString(),
    dueTime = due?.time?.toString(),
    dueTz = due?.zoneId,
    deadline = deadline?.toString(),
    rrule = existing?.rrule,
    recurFromCompletion = existing?.recurFromCompletion ?: false,
    recurrenceEndReason = recurrenceEndReason?.wire,
    recurrenceDtstartDate = existing?.recurrenceDtstartDate,
    recurrenceDtstartTime = existing?.recurrenceDtstartTime,
    recurrenceZoneMode = existing?.recurrenceZoneMode,
    recurrenceZoneId = existing?.recurrenceZoneId,
    recurrenceIntervalUnit = existing?.recurrenceIntervalUnit,
    recurrenceIntervalN = existing?.recurrenceIntervalN,
    recurrencePolicyVersion = existing?.recurrencePolicyVersion,
    durationMin = durationMinutes,
    completedAt = completedAt?.toEpochMilliseconds(),
    isCompletable = isCompletable,
    revision = revision,
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt.toEpochMilliseconds(),
    deletedAt = deletedAt?.toEpochMilliseconds(),
)

fun ProjectEntity.toDomain(): Project = Project(
    id = id,
    name = name,
    description = description,
    color = color,
    parentId = parentId,
    childOrder = childOrder,
    isInbox = isInbox,
    isFavorite = isFavorite,
    archivedAt = archivedAt?.toInstant(),
    revision = revision,
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant(),
    deletedAt = deletedAt?.toInstant(),
)

fun Project.toEntity(viewMode: String = "list"): ProjectEntity = ProjectEntity(
    id = id,
    name = name,
    description = description,
    color = color,
    parentId = parentId,
    childOrder = childOrder,
    isInbox = isInbox,
    inboxMarker = if (isInbox) 1 else null,
    viewMode = viewMode,
    isFavorite = isFavorite,
    archivedAt = archivedAt?.toEpochMilliseconds(),
    revision = revision,
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt.toEpochMilliseconds(),
    deletedAt = deletedAt?.toEpochMilliseconds(),
)

fun LocalWorkspaceEntity.toDomain(): LocalWorkspace = LocalWorkspace(
    workspaceId = workspaceId,
    accountScope = accountScope,
    inboxId = inboxId,
    deviceId = deviceId,
    claimStatus = ClaimStatus.fromWire(claimStatus),
    boundUserId = boundUserId,
    claimOperationId = claimOperationId,
    lastLocalSeq = lastLocalSeq,
    createdAt = createdAt.toInstant(),
)

fun OutboxOperationEntity.toDomain(): OutboxOperation = OutboxOperation(
    operationId = operationId,
    localSeq = localSeq,
    workspaceId = workspaceId,
    type = OperationType.fromWire(opType),
    entityId = entityId,
    fieldPatch = Json.parseToJsonElement(fieldPatch).jsonObject,
    baseRevision = baseRevision,
    deviceId = deviceId,
    createdAt = createdAt.toInstant(),
    status = OutboxStatus.fromWire(status),
    attempts = attempts,
    lastError = lastError,
)

fun ExternalIdentityEntity.toDomain(): ExternalIdentity = ExternalIdentity(
    source = source,
    entityType = EntityType.fromWire(entityType),
    sourceId = sourceId,
    localId = localId,
    sourceHash = sourceHash,
    importVersion = importVersion,
    lastSeenAt = lastSeenAt.toInstant(),
)

fun ExternalIdentity.toEntity(): ExternalIdentityEntity = ExternalIdentityEntity(
    source = source,
    entityType = entityType.wire,
    sourceId = sourceId,
    localId = localId,
    sourceHash = sourceHash,
    importVersion = importVersion,
    lastSeenAt = lastSeenAt.toEpochMilliseconds(),
)
