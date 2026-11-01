package io.github.yozh150991.todo.core.database

import java.io.File
import java.nio.file.Files

/** Creates a fresh directory per test so file-backed databases never share state. */
fun newTempDir(prefix: String): File = Files.createTempDirectory(prefix).toFile().also { it.deleteOnExit() }

/** Writes evidence lines for CI reports into build/reports/runtime-evidence (see build.gradle.kts). */
fun writeEvidence(name: String, lines: List<String>) {
    val dir = System.getProperty("todo.reportDir")?.let(::File) ?: File("build/reports/runtime-evidence")
    dir.mkdirs()
    File(dir, name).writeText(lines.joinToString("\n", postfix = "\n"))
    lines.forEach(::println)
}
