package io.github.yozh150991.todo.core.database

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.sqlite.SQLiteDriver
import io.github.yozh150991.todo.core.database.dao.DeferredTodoistNodeDao
import io.github.yozh150991.todo.core.database.dao.ExternalIdentityDao
import io.github.yozh150991.todo.core.database.dao.LabelDao
import io.github.yozh150991.todo.core.database.dao.OutboxDao
import io.github.yozh150991.todo.core.database.dao.ProjectDao
import io.github.yozh150991.todo.core.database.dao.ReminderDao
import io.github.yozh150991.todo.core.database.dao.ReminderDeliveryDao
import io.github.yozh150991.todo.core.database.dao.SectionDao
import io.github.yozh150991.todo.core.database.dao.TaskActivityDao
import io.github.yozh150991.todo.core.database.dao.TaskDao
import io.github.yozh150991.todo.core.database.dao.WorkspaceDao
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
import kotlin.coroutines.CoroutineContext

@Database(
    entities = [
        LocalWorkspaceEntity::class,
        ProjectEntity::class,
        TaskEntity::class,
        TaskActivityEntity::class,
        OutboxOperationEntity::class,
        ExternalIdentityEntity::class,
        SectionEntity::class,
        LabelEntity::class,
        TaskLabelEntity::class,
        ReminderEntity::class,
        ReminderDeliveryEntity::class,
        DeferredTodoistNodeEntity::class,
    ],
    version = AppDatabase.SCHEMA_VERSION,
    // Every migration must also pass DatabaseOpener's snapshot + verification (FIX-01).
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        // v3 only adds a nullable column and three tables: no data is rewritten.
        AutoMigration(from = 2, to = 3),
    ],
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun projectDao(): ProjectDao
    abstract fun taskDao(): TaskDao
    abstract fun taskActivityDao(): TaskActivityDao
    abstract fun outboxDao(): OutboxDao
    abstract fun externalIdentityDao(): ExternalIdentityDao
    abstract fun sectionDao(): SectionDao
    abstract fun labelDao(): LabelDao
    abstract fun diagnosticsDao(): DiagnosticsDao
    abstract fun reminderDao(): ReminderDao
    abstract fun reminderDeliveryDao(): ReminderDeliveryDao
    abstract fun deferredTodoistNodeDao(): DeferredTodoistNodeDao

    companion object {
        const val SCHEMA_VERSION = 3
        const val DEFAULT_FILE_NAME = "todo.db"
    }
}

// Room's KSP processor generates the `actual` implementation for every target.
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/**
 * Shared builder settings. Each platform passes its driver and journal mode:
 * Android/Desktop — bundled SQLite with WAL (ADR-001); Web — the worker driver with a rollback
 * journal (ADR-004). There is never a destructive migration fallback. On Android/Desktop production
 * code opens the database through [DatabaseOpener], which snapshots and verifies around every upgrade.
 */
internal fun RoomDatabase.Builder<AppDatabase>.buildConfigured(
    driver: SQLiteDriver,
    journalMode: RoomDatabase.JournalMode,
    queryContext: CoroutineContext,
): AppDatabase =
    setDriver(driver)
        .setJournalMode(journalMode)
        .setQueryCoroutineContext(queryContext)
        .build()
