package io.github.yozh150991.todo.core.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * supabase-kt modules on the JVM against a local Supabase stack (CI job "Supabase modules").
 * Skipped unless SUPABASE_URL and SUPABASE_ANON_KEY are set. Evidence: runtime-evidence/supabase-jvm.txt
 */
class JvmSupabaseProbeTest {
    @Test
    fun supabaseModules() = runBlocking<Unit> {
        val url = System.getenv("SUPABASE_URL")
        val key = System.getenv("SUPABASE_ANON_KEY")
        assumeTrue("SUPABASE_URL / SUPABASE_ANON_KEY not set", !url.isNullOrBlank() && !key.isNullOrBlank())

        val platform = "JVM ${System.getProperty("java.version")}"
        val checks = SupabaseProbe.run(url!!, key!!, platform)
        val lines = listOf("[$platform] $url") + checks.map { it.toString() }
        val dir = System.getProperty("todo.reportDir")?.let(::File) ?: File("build/reports/runtime-evidence")
        dir.mkdirs()
        File(dir, "supabase-jvm.txt").writeText(lines.joinToString("\n", postfix = "\n"))
        lines.forEach(::println)
        assertTrue(checks.all { it.passed }, checks.filterNot { it.passed }.joinToString("\n"))
    }
}
