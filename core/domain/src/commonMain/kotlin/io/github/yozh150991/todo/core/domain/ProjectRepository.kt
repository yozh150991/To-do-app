package io.github.yozh150991.todo.core.domain

import io.github.yozh150991.todo.core.database.LocalStore
import io.github.yozh150991.todo.core.database.entity.ProjectEntity
import io.github.yozh150991.todo.core.database.toDomain
import io.github.yozh150991.todo.core.database.toEntity
import io.github.yozh150991.todo.core.model.OperationType
import io.github.yozh150991.todo.core.model.OrderKeys
import io.github.yozh150991.todo.core.model.Project
import io.github.yozh150991.todo.core.model.ProjectRules
import io.github.yozh150991.todo.core.model.ProjectTaskFate
import io.github.yozh150991.todo.core.model.ValidationCode
import io.github.yozh150991.todo.core.model.ValidationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Projects (spec §4.2 `Project`, §5.2; plan Ф1a PR 5): create, edit, nest, archive, delete with a
 * choice for their tasks, and the Undo of each. Reached as [TaskRepository.projects].
 *
 * Rules: the system Inbox is never renamed, moved, nested under, archived or deleted; no cycles
 * in the project tree; archived projects take no new tasks. Archiving or deleting a project does
 * the same to its sub-projects, and Undo brings back exactly the ones that one command changed.
 */
class ProjectRepository internal constructor(private val mutations: Mutations) {

    /** Projects that are neither archived nor in the bin, siblings in their order. */
    fun observeActive(): Flow<List<Project>> = flow {
        emitAll(mutations.store().projects.observeActive().map { rows -> rows.map(ProjectEntity::toDomain) })
    }

    suspend fun get(projectId: String): Project? = mutations.store().projects.getById(projectId)?.toDomain()

    /** Direct sub-projects that are not in the bin (archived ones included). */
    suspend fun children(parentId: String): List<Project> =
        mutations.store().projects.children(parentId).filter { it.deletedAt == null }.map(ProjectEntity::toDomain)

    /** Creates a project at the end of the top level or of [parentId]'s sub-projects. */
    suspend fun create(
        name: String,
        parentId: String? = null,
        color: String? = null,
        isFavorite: Boolean = false,
        description: String = "",
    ): Project {
        val normalized = ProjectRules.normalizeName(name)
        return mutations.write {
            val parent = parentId?.let { requireNestableParent(it) }
            val project = Project(
                id = mutations.ids.newId(),
                name = normalized,
                description = description,
                color = normalizeColor(color),
                parentId = parent?.id,
                childOrder = nextOrder(parent),
                isInbox = false,
                isFavorite = isFavorite,
                createdAt = now,
                updatedAt = now,
            )
            store.projects.insert(project.toEntity())
            record(OperationType.PROJECT_CREATE, project.id, project.snapshot(), null)
            project
        }
    }

    /** Name, description, colour, favourite. The Inbox keeps its name. False when nothing changes. */
    suspend fun update(projectId: String, edit: ProjectEdit): Boolean {
        val name = when (val n = edit.name) {
            FieldEdit.Keep -> null
            is FieldEdit.Set -> ProjectRules.normalizeName(n.value)
        }
        return mutations.write {
            val row = requireLiveProject(projectId)
            if (row.isInbox && name != null && name != row.name) inboxProtected("renamed")
            val next = row.copy(
                name = name ?: row.name,
                description = edit.description.applyTo(row.description),
                color = when (val c = edit.color) {
                    FieldEdit.Keep -> row.color
                    is FieldEdit.Set -> normalizeColor(c.value)
                },
                isFavorite = edit.isFavorite.applyTo(row.isFavorite),
            )
            val changes = FieldChanges()
            changes.diff("name", row.name, next.name) { jsonOf(it) }
            changes.diff("description", row.description, next.description) { jsonOf(it) }
            changes.diff("color", row.color, next.color) { jsonOf(it) }
            changes.diff("is_favorite", row.isFavorite, next.isFavorite) { jsonOf(it) }
            if (changes.isEmpty) return@write false
            store.projects.update(next.copy(updatedAt = nowMillis))
            record(OperationType.PROJECT_UPDATE, projectId, changes.patch(), row.revision)
            true
        }
    }

    /**
     * Nests the project under [parentId], or moves it to the top level when null, after the last
     * sibling there. Its sub-projects and tasks come along unchanged. False when already there.
     */
    suspend fun move(projectId: String, parentId: String?): Boolean = mutations.write {
        val row = requireLiveProject(projectId)
        if (row.isInbox) inboxProtected("moved")
        if (row.parentId == parentId) return@write false
        val parent = parentId?.let { requireNestableParent(it) }
        if (parent != null && store.projectSubtree(row).any { it.id == parent.id }) {
            hierarchy("A project cannot go inside itself or inside its own sub-project")
        }
        val next = row.copy(parentId = parent?.id, childOrder = nextOrder(parent), updatedAt = nowMillis)
        val changes = FieldChanges()
        changes.diff("parent_id", row.parentId, next.parentId) { jsonOf(it) }
        changes.diff("child_order", row.childOrder, next.childOrder) { jsonOf(it) }
        store.projects.update(next)
        record(OperationType.PROJECT_UPDATE, projectId, changes.patch(), row.revision)
        true
    }

    /** Archives the project and its active sub-projects; tasks stay as they are. False when already archived. */
    suspend fun archive(projectId: String): Boolean = mutations.write {
        val row = requireLiveProject(projectId)
        if (row.isInbox) inboxProtected("archived")
        if (row.archivedAt != null) return@write false
        for (project in store.projectSubtree(row) { it.deletedAt == null && it.archivedAt == null }) {
            store.projects.update(project.copy(archivedAt = nowMillis, updatedAt = nowMillis))
            record(OperationType.PROJECT_ARCHIVE, project.id, buildJsonObject { put("archived_at", now.toString()) }, project.revision)
        }
        true
    }

    /**
     * Unarchives the project with the sub-projects archived together with it (Undo of [archive]).
     * An archived parent has to be unarchived first. False when the project is not archived.
     */
    suspend fun unarchive(projectId: String): Boolean = mutations.write {
        val row = requireLiveProject(projectId)
        val archivedAt = row.archivedAt ?: return@write false
        row.parentId?.let { parentId ->
            if (store.projects.getById(parentId)?.archivedAt != null) {
                throw ValidationException("Unarchive the parent project first", ValidationCode.ARCHIVED_PROJECT)
            }
        }
        for (project in store.projectSubtree(row) { it.deletedAt == null && it.archivedAt == archivedAt }) {
            store.projects.update(project.copy(archivedAt = null, updatedAt = nowMillis))
            record(OperationType.PROJECT_UNARCHIVE, project.id, buildJsonObject { put("archived_at", null as String?) }, project.revision)
        }
        true
    }

    /**
     * Bins the project and its sub-projects (spec §4.3 «Delete»: the UI confirms the consequences
     * first). Their tasks either go to the end of the Inbox, each subtree whole and in order, or to
     * the bin together with the project — [fate]. Task operations come first in the outbox, then one
     * `project.delete` per project carrying the fate. Returns null when the project is already binned.
     */
    suspend fun delete(projectId: String, fate: ProjectTaskFate): ProjectDeletion? = mutations.write {
        val row = store.projects.getById(projectId) ?: notFound("Project $projectId")
        if (row.isInbox) inboxProtected("deleted")
        if (row.deletedAt != null) return@write null
        val group = store.projectSubtree(row) { it.deletedAt == null }
        val moves = mutableListOf<TaskMove>()
        var binned = 0
        when (fate) {
            ProjectTaskFate.MOVE_TO_INBOX -> {
                requireProjectForTasks(ws.inboxId)
                for (project in group) {
                    for (task in store.tasks.topLevelInProject(project.id)) {
                        if (task.deletedAt == null) moves += TaskMove(task.id, moveSubtree(task, ws.inboxId, null))
                    }
                }
            }
            ProjectTaskFate.DELETE_WITH_PROJECT -> {
                for (project in group) {
                    for (task in store.tasks.topLevelInProject(project.id)) {
                        if (task.deletedAt == null) binned += binSubtree(task)
                    }
                }
            }
        }
        for (project in group) {
            store.projects.update(project.copy(deletedAt = nowMillis, updatedAt = nowMillis))
            record(
                OperationType.PROJECT_DELETE, project.id,
                buildJsonObject {
                    put("deleted_at", now.toString())
                    put("task_fate", fate.wire)
                },
                project.revision,
            )
        }
        ProjectDeletion(row.id, fate, group.map { it.id }, moves, binned)
    }

    /**
     * Takes the project out of the bin with the sub-projects and tasks binned together with it.
     * Tasks that [delete] moved to the Inbox stay there; [undoDelete] also moves them back.
     * The parent project must not be in the bin. False when the project is not binned.
     */
    suspend fun restore(projectId: String): Boolean = mutations.write { restoreGroup(projectId) }

    /** Undo of [delete]: [restore] plus, for [ProjectTaskFate.MOVE_TO_INBOX], every task back in place. */
    suspend fun undoDelete(deletion: ProjectDeletion): Boolean = mutations.write {
        val restored = restoreGroup(deletion.projectId)
        var moved = false
        for (move in deletion.moves) moved = undoSubtreeMove(move) || moved
        restored || moved
    }

    // --- internals -------------------------------------------------------------------------

    private suspend fun Mutations.Tx.restoreGroup(projectId: String): Boolean {
        val row = store.projects.getById(projectId) ?: notFound("Project $projectId")
        val deletedAt = row.deletedAt ?: return false
        row.parentId?.let { parentId ->
            if (store.projects.getById(parentId)?.deletedAt != null) {
                throw ValidationException("Restore the parent project first", ValidationCode.NOT_FOUND)
            }
        }
        val group = store.projectSubtree(row) { it.deletedAt == deletedAt }
        for (project in group) {
            store.projects.update(project.copy(deletedAt = null, updatedAt = nowMillis))
            record(OperationType.PROJECT_RESTORE, project.id, buildJsonObject { put("deleted_at", null as String?) }, project.revision)
        }
        // Tasks binned by the same delete: parents before their sub-tasks.
        val tasks = group.flatMap { store.tasks.binnedInProjectAt(it.id, deletedAt) }
        tasks.map { it to store.depthOf(it) }.sortedBy { it.second }.forEach { (task, _) -> unbin(task) }
        return true
    }

    private suspend fun Mutations.Tx.requireLiveProject(projectId: String): ProjectEntity {
        val row = store.projects.getById(projectId) ?: notFound("Project $projectId")
        if (row.deletedAt != null) notFound("Project $projectId")
        return row
    }

    /** A project another one may be nested under: live, not archived, not the Inbox. */
    private suspend fun Mutations.Tx.requireNestableParent(parentId: String): ProjectEntity {
        val parent = requireLiveProject(parentId)
        if (parent.isInbox) inboxProtected("a parent of other projects")
        if (parent.archivedAt != null) {
            throw ValidationException("Project $parentId is archived", ValidationCode.ARCHIVED_PROJECT)
        }
        return parent
    }

    private suspend fun Mutations.Tx.nextOrder(parent: ProjectEntity?): String =
        if (parent != null) {
            OrderKeys.after(store.projects.lastChildOrder(parent.id))
        } else {
            OrderKeys.after(store.projects.lastTopLevelOrder())
        }

    private fun inboxProtected(what: String): Nothing =
        throw ValidationException("The Inbox cannot be $what", ValidationCode.INBOX_PROTECTED)

    private fun normalizeColor(color: String?): String? = color?.trim()?.ifEmpty { null }

    private fun Project.snapshot(): JsonObject = buildJsonObject {
        put("id", id)
        put("name", name)
        put("description", description)
        put("color", color)
        put("parent_id", parentId)
        put("child_order", childOrder)
        put("is_inbox", isInbox)
        put("is_favorite", isFavorite)
        put("created_at", createdAt.toString())
    }
}

/** The project and its sub-projects, parents first. [descend] prunes a child with its whole branch. */
internal suspend fun LocalStore.projectSubtree(
    root: ProjectEntity,
    descend: (ProjectEntity) -> Boolean = { true },
): List<ProjectEntity> {
    val result = mutableListOf(root)
    val seen = mutableSetOf(root.id)
    var next = 0
    while (next < result.size) {
        for (child in projects.children(result[next++].id)) {
            if (descend(child) && seen.add(child.id)) result += child
        }
    }
    return result
}
