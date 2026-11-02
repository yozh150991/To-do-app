package io.github.yozh150991.todo.core.testing

/**
 * Exact DDL of Room schema v1 (identity hash `d037c7745652b205c2af0cfe6b3b1963`), generated from
 * `core/database/schemas/.../1.json`. Schema v1 is frozen, so this fixture never changes;
 * it lets migration tests create a genuine v1 database file without the v1 Room classes.
 */
object SchemaV1Fixture {
    const val VERSION = 1
    const val IDENTITY_HASH = "d037c7745652b205c2af0cfe6b3b1963"

    val statements: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `local_workspace` (`workspace_id` TEXT NOT NULL, `account_scope` TEXT NOT NULL, `inbox_id` TEXT NOT NULL, `device_id` TEXT NOT NULL, `claim_status` TEXT NOT NULL, `bound_user_id` TEXT, `claim_operation_id` TEXT, `backup_checkpoint` TEXT, `last_local_seq` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `singleton` INTEGER NOT NULL, PRIMARY KEY(`workspace_id`))",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_local_workspace_singleton` ON `local_workspace` (`singleton`)",
        "CREATE TABLE IF NOT EXISTS `project` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `color` TEXT, `parent_id` TEXT, `child_order` TEXT NOT NULL, `is_inbox` INTEGER NOT NULL, `inbox_marker` INTEGER, `view_mode` TEXT NOT NULL, `is_favorite` INTEGER NOT NULL, `archived_at` INTEGER, `revision` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`parent_id`) REFERENCES `project`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
        "CREATE INDEX IF NOT EXISTS `index_project_parent_id` ON `project` (`parent_id`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_project_inbox_marker` ON `project` (`inbox_marker`)",
        "CREATE INDEX IF NOT EXISTS `index_project_deleted_at` ON `project` (`deleted_at`)",
        "CREATE TABLE IF NOT EXISTS `task` (`id` TEXT NOT NULL, `project_id` TEXT NOT NULL, `section_id` TEXT, `parent_id` TEXT, `child_order` TEXT NOT NULL, `day_order` TEXT, `content` TEXT NOT NULL, `description` TEXT NOT NULL, `source_url` TEXT, `priority` INTEGER NOT NULL, `due_date` TEXT, `due_time` TEXT, `due_tz` TEXT, `deadline` TEXT, `rrule` TEXT, `recur_from_completion` INTEGER NOT NULL, `recurrence_end_reason` TEXT, `recurrence_dtstart_date` TEXT, `recurrence_dtstart_time` TEXT, `recurrence_zone_mode` TEXT, `recurrence_zone_id` TEXT, `recurrence_interval_unit` TEXT, `recurrence_interval_n` INTEGER, `recurrence_policy_version` INTEGER, `duration_min` INTEGER, `completed_at` INTEGER, `is_completable` INTEGER NOT NULL, `revision` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`project_id`) REFERENCES `project`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`parent_id`) REFERENCES `task`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
        "CREATE INDEX IF NOT EXISTS `index_task_project_id` ON `task` (`project_id`)",
        "CREATE INDEX IF NOT EXISTS `index_task_parent_id` ON `task` (`parent_id`)",
        "CREATE INDEX IF NOT EXISTS `index_task_due_date` ON `task` (`due_date`)",
        "CREATE INDEX IF NOT EXISTS `index_task_deadline` ON `task` (`deadline`)",
        "CREATE INDEX IF NOT EXISTS `index_task_deleted_at` ON `task` (`deleted_at`)",
        "CREATE TABLE IF NOT EXISTS `task_activity` (`id` TEXT NOT NULL, `task_id` TEXT NOT NULL, `event_type` TEXT NOT NULL, `field_changes` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `device_id` TEXT NOT NULL, `operation_id` TEXT, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_task_activity_task_id` ON `task_activity` (`task_id`)",
        "CREATE INDEX IF NOT EXISTS `index_task_activity_operation_id` ON `task_activity` (`operation_id`)",
        "CREATE TABLE IF NOT EXISTS `outbox_operation` (`operation_id` TEXT NOT NULL, `local_seq` INTEGER NOT NULL, `workspace_id` TEXT NOT NULL, `entity` TEXT NOT NULL, `entity_id` TEXT NOT NULL, `op_type` TEXT NOT NULL, `field_patch` TEXT NOT NULL, `base_revision` INTEGER, `device_id` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `status` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `last_error` TEXT, PRIMARY KEY(`operation_id`))",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_outbox_operation_workspace_id_local_seq` ON `outbox_operation` (`workspace_id`, `local_seq`)",
        "CREATE INDEX IF NOT EXISTS `index_outbox_operation_status_local_seq` ON `outbox_operation` (`status`, `local_seq`)",
        "CREATE INDEX IF NOT EXISTS `index_outbox_operation_entity_entity_id` ON `outbox_operation` (`entity`, `entity_id`)",
        "CREATE TABLE IF NOT EXISTS `external_identity` (`source` TEXT NOT NULL, `entity_type` TEXT NOT NULL, `source_id` TEXT NOT NULL, `local_id` TEXT NOT NULL, `source_hash` TEXT NOT NULL, `import_version` INTEGER NOT NULL, `last_seen_at` INTEGER NOT NULL, PRIMARY KEY(`source`, `entity_type`, `source_id`))",
        "CREATE INDEX IF NOT EXISTS `index_external_identity_entity_type_local_id` ON `external_identity` (`entity_type`, `local_id`)",
        "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
        "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'd037c7745652b205c2af0cfe6b3b1963')",
    )
}
