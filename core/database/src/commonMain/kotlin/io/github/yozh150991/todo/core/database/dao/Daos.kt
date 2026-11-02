package io.github.yozh150991.todo.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import io.github.yozh150991.todo.core.database.entity.ExternalIdentityEntity
import io.github.yozh150991.todo.core.database.entity.LabelEntity
import io.github.yozh150991.todo.core.database.entity.LocalWorkspaceEntity
import io.github.yozh150991.todo.core.database.entity.OutboxOperationEntity
import io.github.yozh150991.todo.core.database.entity.ProjectEntity
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

    @Query("SELECT * FROM project WHERE is_inbox = 1 AND deleted_at IS NULL")
    suspend fun getInbox(): ProjectEntity?

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
