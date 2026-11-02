package io.github.yozh150991.todo.android

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yozh150991.todo.core.database.createAppDatabase
import io.github.yozh150991.todo.core.testing.DatabaseScenarios
import io.github.yozh150991.todo.core.testing.DatabaseTestEnv
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Ф0 Android gate: the same scenarios as the JVM run, on a real Android runtime with the bundled
 * SQLite driver. CI collects the `TodoEvidence` log lines into the PR report.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun env(name: String): DatabaseTestEnv {
        val dir = File(context.cacheDir, "db-tests/$name-${System.nanoTime()}").apply {
            deleteRecursively()
            mkdirs()
        }
        return DatabaseTestEnv(
            dir = dir.absolutePath,
            platform = "Android API ${Build.VERSION.SDK_INT} ${Build.SUPPORTED_ABIS.firstOrNull()}",
            build = { path -> createAppDatabase(context, File(path)) },
            log = { line -> Log.i(EVIDENCE_TAG, line) },
            fileSize = { path -> File(path).let { if (it.exists()) it.length() else 0L } },
            writeText = { path, text -> File(path).writeText(text) },
        )
    }

    @Test fun sqliteRuntimeEvidence() = DatabaseScenarios.sqliteRuntimeEvidence(env("sqlite"))

    @Test fun persistsAcrossReopen() = runBlocking { DatabaseScenarios.persistsAcrossReopen(env("reopen")) }

    @Test fun outboxFailureRollsBack() = runBlocking { DatabaseScenarios.outboxFailureRollsBack(env("rollback")) }

    @Test fun concurrentWritesKeepSequence() =
        runBlocking { DatabaseScenarios.concurrentWritesKeepSequence(env("concurrent"), writes = 50) }

    @Test fun migrationFromV1PreservesData() =
        runBlocking { DatabaseScenarios.migrationFromV1PreservesData(env("migration")) }

    @Test fun walFramesAreCapturedBySnapshot() =
        runBlocking { DatabaseScenarios.walFramesAreCapturedBySnapshot(env("wal")) }

    @Test fun failedVerificationRestoresSnapshot() =
        runBlocking { DatabaseScenarios.failedVerificationRestoresSnapshot(env("restore")) }

    @Test fun interruptedUpgradeIsRecovered() =
        runBlocking { DatabaseScenarios.interruptedUpgradeIsRecovered(env("crash")) }

    @Test fun insufficientSpaceLeavesDatabaseUntouched() =
        runBlocking { DatabaseScenarios.insufficientSpaceLeavesDatabaseUntouched(env("space")) }

    @Test fun downgradeIsRefused() = runBlocking { DatabaseScenarios.downgradeIsRefused(env("downgrade")) }

    @Test fun leftoverTemporaryFilesAreRemoved() =
        runBlocking { DatabaseScenarios.leftoverTemporaryFilesAreRemoved(env("leftover")) }

    private companion object {
        const val EVIDENCE_TAG = "TodoEvidence"
    }
}
