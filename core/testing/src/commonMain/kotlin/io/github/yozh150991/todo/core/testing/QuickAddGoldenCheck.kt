package io.github.yozh150991.todo.core.testing

import io.github.yozh150991.todo.core.domain.quickadd.QuickAddParser
import io.github.yozh150991.todo.core.domain.quickadd.QuickAddProject
import io.github.yozh150991.todo.core.domain.quickadd.QuickAddResult
import io.github.yozh150991.todo.core.domain.quickadd.QuickAddWarning

/**
 * Runs [QuickAddParser] over every golden phrase and lists the differences. Plain Kotlin with no
 * test framework, so the JVM tests, the Android device tests and the Web self-test can all run the
 * same table (plan Ф1a PR 6: the same tests on JVM, Android, Web).
 */
object QuickAddGoldenCheck {
    /** Golden projects with their names as IDs. */
    val projects: List<QuickAddProject> = QuickAddGoldenPhrases.projects.map { QuickAddProject(it, it) }

    fun parse(case: QuickAddGoldenPhrases.Case): QuickAddResult =
        QuickAddParser.parse(case.input, QuickAddGoldenPhrases.now, projects)

    /** One line per case whose result differs from the table; empty when the parser follows it. */
    fun failures(): List<String> = QuickAddGoldenPhrases.all.mapNotNull { case ->
        val e = case.expected
        val r = parse(case)
        val diffs = buildList {
            if (r.content != e.content) add("content '${r.content}' != '${e.content}'")
            if (r.date != e.date) add("date ${r.date} != ${e.date}")
            if (r.time != e.time) add("time ${r.time} != ${e.time}")
            if (r.project?.name != e.project) add("project ${r.project?.name} != ${e.project}")
            if (r.priority != e.priority) add("priority ${r.priority} != ${e.priority}")
            val expectedWarnings = listOfNotNull(e.warning?.let { QuickAddWarning.valueOf(it.name) }).toSet()
            if (r.warnings != expectedWarnings) add("warnings ${r.warnings} != $expectedWarnings")
            if (r.alternatives != e.alternatives) add("alternatives ${r.alternatives} != ${e.alternatives}")
            if (e.recognisesNothing != r.recognisesNothing) add("tokens ${r.tokens}")
        }
        if (diffs.isEmpty()) null else "${case.id} \"${case.input}\": ${diffs.joinToString("; ")}"
    }
}
