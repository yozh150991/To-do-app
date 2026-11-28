package io.github.yozh150991.todo.core.testing

/**
 * Seed data of a genuine schema v2 database — what `v0.0.1`/`v0.0.2` leave on a phone — plus the
 * v2-only parts (section, label, task label, a duration in minutes) so the v2 → v3 upgrade has to
 * carry all of them. Plain SQL, so it runs through any SQLite driver.
 */
object V2Database {
    const val WORKSPACE_ID = "ws-2"
    const val INBOX_ID = "inbox"
    const val SEEDED_LOCAL_SEQ = 4L

    val expectedCounts = mapOf(
        "local_workspace" to 1L,
        "project" to 2L,
        "task" to 4L,
        "task_activity" to 1L,
        "outbox_operation" to 4L,
        "external_identity" to 0L,
        "section" to 1L,
        "label" to 1L,
        "task_label" to 1L,
    )

    val seedStatements: List<String> = buildList {
        add(
            "INSERT INTO local_workspace (workspace_id, account_scope, inbox_id, device_id, claim_status, " +
                "bound_user_id, claim_operation_id, backup_checkpoint, last_local_seq, created_at, singleton) " +
                "VALUES ('$WORKSPACE_ID', 'local:$WORKSPACE_ID', '$INBOX_ID', 'device-2', 'local', NULL, NULL, NULL, " +
                "$SEEDED_LOCAL_SEQ, 1000, 1)",
        )
        listOf(Triple(INBOX_ID, "Inbox", 1), Triple("work", "Робота", 0)).forEachIndexed { i, (id, name, inbox) ->
            add(
                "INSERT INTO project (id, name, description, color, parent_id, child_order, is_inbox, inbox_marker, " +
                    "view_mode, is_favorite, archived_at, revision, created_at, updated_at, deleted_at) VALUES " +
                    "('$id', '$name', '', NULL, NULL, 'a$i', $inbox, ${if (inbox == 1) "1" else "NULL"}, 'list', 0, " +
                    "NULL, NULL, 1000, 1000, NULL)",
            )
        }
        add(
            "INSERT INTO section (id, project_id, name, description, section_order, collapsed, revision, created_at, " +
                "updated_at, deleted_at) VALUES ('s1', 'work', 'Звіти', '', 'a0', 0, NULL, 1000, 1000, NULL)",
        )
        add(
            "INSERT INTO label (id, name, color, item_order, is_favorite, revision, created_at, updated_at, deleted_at) " +
                "VALUES ('l1', 'дім', NULL, 'a0', 0, NULL, 1000, 1000, NULL)",
        )
        // id, project, section, parent, order, content, due date, due time, zone, duration_min, completed_at
        val tasks = listOf(
            listOf("t1", INBOX_ID, null, null, "a0", "test", null, null, null, null, null),
            listOf("t2", "work", "s1", null, "a0", "Zapłacić rachunek", "2026-11-21", "10:00", "Europe/Warsaw", "30", null),
            listOf("t3", "work", "s1", "t2", "a0", "Знайти рахунок", null, null, null, null, null),
            listOf("t4", INBOX_ID, null, null, "a1", "Done task", null, null, null, null, "2000"),
        )
        tasks.forEach { row ->
            val text = row.take(9).joinToString(", ") { v -> if (v == null) "NULL" else "'$v'" }
            val numbers = row.drop(9).joinToString(", ") { v -> v ?: "NULL" }
            add(
                "INSERT INTO task (id, project_id, section_id, parent_id, child_order, content, due_date, due_time, " +
                    "due_tz, duration_min, completed_at, description, priority, recur_from_completion, is_completable, " +
                    "created_at, updated_at) VALUES ($text, $numbers, '', 4, 0, 1, 1000, 1000)",
            )
        }
        add("INSERT INTO task_label (task_id, label_id, created_at) VALUES ('t2', 'l1', 1000)")
        (1..SEEDED_LOCAL_SEQ.toInt()).forEach { seq ->
            val entity = if (seq == 1) "project" else "task"
            add(
                "INSERT INTO outbox_operation (operation_id, local_seq, workspace_id, entity, entity_id, op_type, " +
                    "field_patch, base_revision, device_id, created_at, status, attempts, last_error) VALUES " +
                    "('op-$seq', $seq, '$WORKSPACE_ID', '$entity', 't1', '$entity.create', '{}', NULL, 'device-2', " +
                    "1000, 'pending', 0, NULL)",
            )
        }
        add(
            "INSERT INTO task_activity (id, task_id, event_type, field_changes, created_at, device_id, operation_id) " +
                "VALUES ('a1', 't1', 'created', '{}', 1000, 'device-2', 'op-2')",
        )
    }
}
