package io.github.yozh150991.todo.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import io.github.yozh150991.todo.core.database.entity.DeferredTodoistNodeEntity
import io.github.yozh150991.todo.core.database.entity.ExternalIdentityEntity
import io.github.yozh150991.todo.core.database.entity.LabelEntity
import io.github.yozh150991.todo.core.database.entity.LocalWorkspaceEntity
import io.github.yozh150991.todo.core.database.entity.OutboxOperationEntity
import io.github.yozh150991.todo.core.database.entity.ProjectEntity
import io.github.yozh150991.todo.core.database.entity.ReminderDeliveryEntity
import io.github.yozh150991.todo.core.database.entity.ReminderEntity
import io.github.yozh150991.todo.core.database.entity.SectionEntity
import io.github.yozh150991.todo.core.database.entity.TaskActivityEntity
import io.github.yozh150991.todo.core.database.entity.TaskEntity
import io.github.yozh150991.todo.core.database.entity.TaskLabelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM local_workspace LIMIT 1")
    suspend fun get(): LocalWorkspaceEntity?

    @Insert
    suspend fun insert(workspace: LocalWorkspaceEntity)

    /** Must run inside a write transaction together with the outbox insert that uses the new value. */
    @Query("UPDATE local_workspace SET last_local_seq = last_local_seq + 1 WHERE workspace_id = :workspaceId")
    suspend fun incrementLocalSeq(workspaceId: String): Int

    @Query("SELECT last_local_seq FROM local_workspace WHERE workspace_id = :workspaceId")
    suspend fun lastLocalSeq(workspaceId: String): Long
}

@Dao
interface ProjectDao {
    @Insert
    suspend fun insert(project: ProjectEntity)

    @Query("SELECT * FROM project WHERE id = :id")
    suspend fun getById(id: String): ProjectEntity?

    @Update
    suspend fun update(project: ProjectEntity): Int

    @Query("SELECT * FROM project WHERE is_inbox = 1 AND deleted_at IS NULL")
    suspend fun getInbox(): ProjectEntity?

    /** Direct sub-projects, including archived and deleted ones (callers filter). */
    @Query("SELECT * FROM project WHERE parent_id = :parentId ORDER BY child_order, id")
    suspend fun children(parentId: String): List<ProjectEntity>

    @Query("SELECT MAX(child_order) FROM project WHERE parent_id IS NULL")
    suspend fun lastTopLevelOrder(): String?

    @Query("SELECT MAX(child_order) FROM project WHERE parent_id = :parentId")
    suspend fun lastChildOrder(parentId: String): String?

    /** Projects that are neither archived nor deleted, in tree order of siblings. */
    @Query("SELECT * FROM project WHERE deleted_at IS NULL AND archived_at IS NULL ORDER BY child_order, id")
    fun observeActive(): Flow<List<ProjectEntity>>

    /** Every row in any state, by id (backup, invariant checks in tests). */
    @Query("SELECT * FROM project ORDER BY id")
    suspend fun all(): List<ProjectEntity>

    @Query("SELECT COUNT(*) FROM project")
    suspend fun count(): Int
}

@Dao
interface TaskDao {
    @Insert
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity): Int

    @Query("SELECT * FROM task WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    /** One task, re-emitted on every change (task details). */
    @Query("SELECT * FROM task WHERE id = :id")
    fun observeById(id: String): Flow<TaskEntity?>

    @Query(
        """
        SELECT * FROM task
        WHERE project_id = :projectId AND deleted_at IS NULL AND completed_at IS NULL
        ORDER BY child_order
        """,
    )
    fun observeOpenInProject(projectId: String): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM task
        WHERE project_id = :projectId AND deleted_at IS NULL AND completed_at IS NOT NULL
        ORDER BY completed_at DESC
        """,
    )
    fun observeCompletedInProject(projectId: String): Flow<List<TaskEntity>>

    /** Largest sibling order key at the top level of a project, used to append new tasks. */
    @Query("SELECT MAX(child_order) FROM task WHERE project_id = :projectId AND parent_id IS NULL")
    suspend fun lastTopLevelOrder(projectId: String): String?

    /** Largest order key among the sub-tasks of [parentId], used to append a sub-task. */
    @Query("SELECT MAX(child_order) FROM task WHERE parent_id = :parentId")
    suspend fun lastChildOrder(parentId: String): String?

    /** Direct sub-tasks in any state (open, completed, deleted); callers filter. */
    @Query("SELECT * FROM task WHERE parent_id = :parentId ORDER BY child_order, id")
    suspend fun children(parentId: String): List<TaskEntity>

    /** Top-level tasks of a project in any state, in list order. */
    @Query("SELECT * FROM task WHERE project_id = :projectId AND parent_id IS NULL ORDER BY child_order, id")
    suspend fun topLevelInProject(projectId: String): List<TaskEntity>

    /** Tasks of a project that are not in the bin (open and completed). */
    @Query("SELECT * FROM task WHERE project_id = :projectId AND deleted_at IS NULL ORDER BY child_order, id")
    suspend fun liveInProject(projectId: String): List<TaskEntity>

    /** Tasks of a project binned at exactly [deletedAt] — by one command, e.g. with their project. */
    @Query("SELECT * FROM task WHERE project_id = :projectId AND deleted_at = :deletedAt ORDER BY child_order, id")
    suspend fun binnedInProjectAt(projectId: String, deletedAt: Long): List<TaskEntity>

    /** Every row in any state, by id (backup, invariant checks in tests). */
    @Query("SELECT * FROM task ORDER BY id")
    suspend fun all(): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM task")
    suspend fun count(): Int
}

@Dao
interface TaskActivityDao {
    @Insert
    suspend fun insert(activity: TaskActivityEntity)

    @Query("SELECT * FROM task_activity WHERE task_id = :taskId ORDER BY created_at, id")
    suspend fun forTask(taskId: String): List<TaskActivityEntity>

    @Query("SELECT COUNT(*) FROM task_activity")
    suspend fun count(): Int
}

@Dao
interface OutboxDao {
    @Insert
    suspend fun insert(operation: OutboxOperationEntity)

    @Query("SELECT * FROM outbox_operation WHERE status = 'pending' ORDER BY local_seq")
    suspend fun pending(): List<OutboxOperationEntity>

    @Query("SELECT * FROM outbox_operation ORDER BY local_seq")
    suspend fun all(): List<OutboxOperationEntity>

    @Query("SELECT * FROM outbox_operation WHERE entity = :entity AND entity_id = :entityId ORDER BY local_seq")
    suspend fun forEntity(entity: String, entityId: String): List<OutboxOperationEntity>

    @Query("SELECT COUNT(*) FROM outbox_operation WHERE status = 'pending'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox_operation WHERE status = 'pending'")
    suspend fun pendingCount(): Int

    @Query("SELECT COUNT(*) FROM outbox_operation")
    suspend fun count(): Int
}

@Dao
interface ExternalIdentityDao {
    @Insert
    suspend fun insert(identity: ExternalIdentityEntity)

    @Query(
        "SELECT * FROM external_identity WHERE source = :source AND entity_type = :entityType AND source_id = :sourceId",
    )
    suspend fun find(source: String, entityType: String, sourceId: String): ExternalIdentityEntity?

    @Query("SELECT * FROM external_identity WHERE entity_type = :entityType AND local_id = :localId")
    suspend fun forLocalId(entityType: String, localId: String): List<ExternalIdentityEntity>
}

@Dao
interface SectionDao {
    @Insert
    suspend fun insert(section: SectionEntity)

    @Query("SELECT * FROM section WHERE id = :id")
    suspend fun getById(id: String): SectionEntity?

    @Query("SELECT * FROM section WHERE project_id = :projectId AND deleted_at IS NULL ORDER BY section_order")
    suspend fun forProject(projectId: String): List<SectionEntity>

    @Query("SELECT COUNT(*) FROM section")
    suspend fun count(): Int
}

@Dao
interface LabelDao {
    @Insert
    suspend fun insert(label: LabelEntity)

    @Query("SELECT * FROM label WHERE id = :id")
    suspend fun getById(id: String): LabelEntity?

    @Insert
    suspend fun attach(link: TaskLabelEntity)

    @Query("DELETE FROM task_label WHERE task_id = :taskId AND label_id = :labelId")
    suspend fun detach(taskId: String, labelId: String): Int

    @Query("SELECT label_id FROM task_label WHERE task_id = :taskId ORDER BY label_id")
    suspend fun labelIdsForTask(taskId: String): List<String>

    @Query("SELECT COUNT(*) FROM label")
    suspend fun count(): Int
}

@Dao
interface ReminderDao {
    @Insert
    suspend fun insert(reminder: ReminderEntity)

    @Update
    suspend fun update(reminder: ReminderEntity): Int

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun getById(id: String): ReminderEntity?

    @Query("SELECT * FROM reminder WHERE task_id = :taskId AND deleted_at IS NULL ORDER BY created_at, id")
    suspend fun forTask(taskId: String): List<ReminderEntity>

    @Query("SELECT COUNT(*) FROM reminder")
    suspend fun count(): Int
}

@Dao
interface ReminderDeliveryDao {
    @Insert
    suspend fun insert(delivery: ReminderDeliveryEntity)

    @Update
    suspend fun update(delivery: ReminderDeliveryEntity): Int

    @Query("SELECT * FROM reminder_delivery WHERE reminder_id = :reminderId ORDER BY planned_at, id")
    suspend fun forReminder(reminderId: String): List<ReminderDeliveryEntity>

    /** Deliveries still to fire, for re-registering alarms after a reboot or an update (Ф1a PR 7). */
    @Query("SELECT * FROM reminder_delivery WHERE fired_at IS NULL AND dismissed_at IS NULL ORDER BY planned_at, id")
    suspend fun pending(): List<ReminderDeliveryEntity>

    @Query("SELECT COUNT(*) FROM reminder_delivery")
    suspend fun count(): Int
}

@Dao
interface DeferredTodoistNodeDao {
    @Insert
    suspend fun insertAll(nodes: List<DeferredTodoistNodeEntity>)

    @Query("SELECT * FROM deferred_todoist_node ORDER BY root_recurring_source_id, depth, source_id")
    suspend fun all(): List<DeferredTodoistNodeEntity>

    @Query("SELECT COUNT(*) FROM deferred_todoist_node WHERE depth = 0")
    suspend fun rootCount(): Int

    @Query("SELECT COUNT(*) FROM deferred_todoist_node")
    suspend fun count(): Int
}
