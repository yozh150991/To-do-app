package io.github.yozh150991.todo.core.testing

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Golden time fixtures (spec §4.0.1, TIME-01..04, TIME-08) on the JVM. Evidence: runtime-evidence/time-jvm.txt */
class JvmTimeGoldenFixturesTest {
    @Test
    fun goldenTimeFixtures() {
        val results = TimeGoldenFixtures.runAll()
        val dir = System.getProperty("todo.reportDir")?.let(::File) ?: File("build/reports/runtime-evidence")
        dir.mkdirs()
        val lines = listOf("[JVM ${System.getProperty("java.version")}, tzdb ${tzdbVersion()}]") + results.map { it.toString() }
        File(dir, "time-jvm.txt").writeText(lines.joinToString("\n", postfix = "\n"))
        lines.forEach(::println)
        assertTrue(results.all { it.passed }, results.filterNot { it.passed }.joinToString("\n"))
    }

    private fun tzdbVersion(): String =
        runCatching { java.time.zone.ZoneRulesProvider.getVersions("Europe/Warsaw").lastKey() }.getOrDefault("?")
}
