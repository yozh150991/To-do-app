package io.github.yozh150991.todo.core.testing

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Quick Add golden table is consistent with its own rules (R1–R11) before any parser exists.
 * Evidence: runtime-evidence/quick-add-golden.txt (case counts per language).
 */
class QuickAddGoldenPhrasesTest {
    @Test
    fun tableFollowsItsRules() {
        val problems = QuickAddGoldenPhrases.problems()
        val byLanguage = QuickAddGoldenPhrases.all.groupBy { it.language }
        val lines = buildList {
            add("Quick Add golden phrases (spec §5.4, Ф1a): ${QuickAddGoldenPhrases.all.size} cases, reference ${QuickAddGoldenPhrases.now} ${QuickAddGoldenPhrases.zone}")
            byLanguage.forEach { (language, cases) ->
                val plain = cases.count { it.expected.recognisesNothing }
                add("  $language: ${cases.size} cases, $plain stay plain text")
            }
            add(if (problems.isEmpty()) "table: consistent" else "table problems: $problems")
        }
        val dir = System.getProperty("todo.reportDir")?.let(::File) ?: File("build/reports/runtime-evidence")
        dir.mkdirs()
        File(dir, "quick-add-golden.txt").writeText(lines.joinToString("\n", postfix = "\n"))
        assertEquals(emptyList(), problems)
    }
}
