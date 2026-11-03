package io.github.yozh150991.todo.core.testing

import io.github.yozh150991.todo.core.database.createAppDatabase
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test

/** Runs the shared Ф0 scenarios on the JVM / Desktop target. Evidence: build/reports/runtime-evidence/jvm.txt */
class JvmDatabaseScenariosTest {

    private fun env(name: String): DatabaseTestEnv {
        val dir = Files.createTempDirectory("scenario-$name").toFile()
        return DatabaseTestEnv(
            dir = dir.absolutePath,
            platform = "JVM ${System.getProperty("java.version")} ${System.getProperty("os.name")}",
            build = { path -> createAppDatabase(File(path)) },
            log = { line -> evidence(line) },
            fileSize = { path -> File(path).let { if (it.exists()) it.length() else 0L } },
            writeText = { path, text -> File(path).writeText(text) },
        )
    }

    @Test fun sqliteRuntimeEvidence() = DatabaseScenarios.sqliteRuntimeEvidence(env("sqlite"))

    @Test fun diagnosticsThroughRoom() = runBlocking { DatabaseScenarios.diagnosticsThroughRoom(env("diagnostics")) }

    @Test fun persistsAcrossReopen() = runBlocking { DatabaseScenarios.persistsAcrossReopen(env("reopen")) }

    @Test fun outboxFailureRollsBack() = runBlocking { DatabaseScenarios.outboxFailureRollsBack(env("rollback")) }

    @Test fun concurrentWritesKeepSequence() =
        runBlocking { DatabaseScenarios.concurrentWritesKeepSequence(env("concurrent"), writes = 100) }

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

    companion object {
        private val evidenceFile: File by lazy {
            val dir = System.getProperty("todo.reportDir")?.let(::File) ?: File("build/reports/runtime-evidence")
            dir.mkdirs()
            File(dir, "jvm.txt").also { it.writeText("") }
        }

        @Synchronized
        fun evidence(line: String) {
            println(line)
            evidenceFile.appendText(line + "\n")
        }
    }
}
