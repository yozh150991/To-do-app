package io.github.yozh150991.todo.ui.tasklist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yozh150991.todo.core.model.Priority
import io.github.yozh150991.todo.core.model.Task
import io.github.yozh150991.todo.ui.resources.Res
import io.github.yozh150991.todo.ui.resources.complete_task
import io.github.yozh150991.todo.ui.resources.deadline_label
import io.github.yozh150991.todo.ui.resources.has_description
import io.github.yozh150991.todo.ui.resources.ic_deadline
import io.github.yozh150991.todo.ui.resources.ic_description
import io.github.yozh150991.todo.ui.resources.priority_spoken
import io.github.yozh150991.todo.ui.resources.uncomplete_task
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** A task in a list with its depth in the sub-task tree (0 = top level). */
data class TaskItem(val task: Task, val depth: Int = 0)

/**
 * Tasks in tree order: each task followed by its sub-tasks, siblings by `child_order`. A task whose
 * parent is not in [tasks] (completed, filtered out) starts its own branch at the top level.
 */
fun treeOrder(tasks: List<Task>): List<TaskItem> {
    val ids = tasks.mapTo(HashSet()) { it.id }
    val children = tasks.filter { it.parentId != null && it.parentId in ids }.groupBy { it.parentId }
    val result = ArrayList<TaskItem>(tasks.size)
    val seen = HashSet<String>()
    fun visit(task: Task, depth: Int) {
        if (!seen.add(task.id)) return
        result += TaskItem(task, depth)
        children[task.id].orEmpty().sortedWith(compareBy({ it.childOrder }, { it.id })).forEach { visit(it, depth + 1) }
    }
    tasks.filter { it.parentId == null || it.parentId !in ids }
        .sortedWith(compareBy({ it.childOrder }, { it.id }))
        .forEach { visit(it, 0) }
    return result
}

/** Own palette (spec §1.3): p1–p3 coloured, p4 neutral; colour is never the only sign (UX_SPEC §3.1). */
@Composable
fun priorityColor(priority: Priority): Color = when (priority) {
    Priority.P1 -> Color(0xFFC62828)
    Priority.P2 -> Color(0xFFEF6C00)
    Priority.P3 -> Color(0xFF1565C0)
    Priority.P4 -> MaterialTheme.colorScheme.outline
}

/**
 * A task row (UX_SPEC §3.1): the checkbox in the priority colour, the title in up to two lines,
 * and a meta line with the date (overdue in the error colour), the deadline with what is left, the
 * project where the list mixes projects, and a mark for a description. At least 48 dp high.
 */
@Composable
fun TaskRow(
    item: TaskItem,
    today: LocalDate,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    projectName: String? = null,
    selected: Boolean = false,
) {
    val task = item.task
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(background)
            .clickable(onClick = onOpen)
            .padding(start = 8.dp + 24.dp * item.depth, end = 16.dp, top = 4.dp, bottom = 4.dp)
            .testTag("task-row-${task.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val name = if (task.isCompleted) {
            stringResource(Res.string.uncomplete_task, task.content)
        } else {
            stringResource(Res.string.complete_task, task.content)
        }
        val spoken = "$name, ${stringResource(Res.string.priority_spoken, task.priority.level)}"
        val color = priorityColor(task.priority)
        Checkbox(
            checked = task.isCompleted,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = color, uncheckedColor = color),
            modifier = Modifier.semantics { contentDescription = spoken },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                task.content,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            MetaLine(task, today, projectName)
        }
    }
}

@Composable
private fun MetaLine(task: Task, today: LocalDate, projectName: String?) {
    val due = task.due
    val deadline = task.deadline
    if (due == null && deadline == null && projectName == null && task.description.isEmpty()) return
    val small = MaterialTheme.typography.labelMedium
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (due != null) {
            val overdue = !task.isCompleted && due.date < today
            Text(dueText(due, today), style = small, color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, maxLines = 1)
        }
        if (deadline != null) {
            val left = deadlineDaysLeft(deadline, today)
            val text = deadlineText(left)
            val spoken = stringResource(Res.string.deadline_label, text)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { contentDescription = spoken }) {
                val tint = if (left < 0 && !task.isCompleted) MaterialTheme.colorScheme.error else muted
                Icon(painterResource(Res.drawable.ic_deadline), contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                Text(text, style = small, color = tint, maxLines = 1, modifier = Modifier.padding(start = 2.dp))
            }
        }
        if (projectName != null) Text(projectName, style = small, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (task.description.isNotEmpty()) {
            Icon(
                painterResource(Res.drawable.ic_description),
                contentDescription = stringResource(Res.string.has_description),
                tint = muted,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
