package io.github.yozh150991.todo.core.testing

import io.github.yozh150991.todo.core.domain.quickadd.QuickAddParser
import io.github.yozh150991.todo.core.domain.quickadd.QuickAddTokenKind
import io.github.yozh150991.todo.core.domain.quickadd.QuickAddWarning
import io.github.yozh150991.todo.core.model.Priority
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Basic Quick Add parser (spec §5.4, plan Ф1a PR 6) against the golden table, plus the guarantees
 * the table cannot list: no word is ever lost, and a dismissed phrase stays in the title.
 * Evidence: runtime-evidence/quick-add-parser.txt.
 */
class QuickAddParserTest {

    private val now = QuickAddGoldenPhrases.now
    private val projects = QuickAddGoldenCheck.projects

    @Test
    fun followsTheGoldenTable() {
        val failures = QuickAddGoldenCheck.failures()
        val byLanguage = QuickAddGoldenPhrases.all.groupBy { it.language }
        val lines = buildList {
            add("Quick Add parser (Ф1a): ${QuickAddGoldenPhrases.all.size - failures.size}/${QuickAddGoldenPhrases.all.size} golden phrases")
            byLanguage.forEach { (language, cases) ->
                val failed = failures.count { it.startsWith("QA-${language.name}-") }
                add("  $language: ${cases.size - failed}/${cases.size}")
            }
            failures.forEach { add("  FAIL $it") }
        }
        val dir = System.getProperty("todo.reportDir")?.let(::File) ?: File("build/reports/runtime-evidence")
        dir.mkdirs()
        File(dir, "quick-add-parser.txt").writeText(lines.joinToString("\n", postfix = "\n"))
        assertEquals(emptyList(), failures)
    }

    @Test
    fun tokensPointAtTheTypedPhrases() {
        val text = "Pay rent tomorrow at 9am #Finanse p1"
        val result = QuickAddParser.parse(text, now, projects)
        assertEquals(
            listOf(
                QuickAddTokenKind.DATE to "tomorrow",
                QuickAddTokenKind.TIME to "at 9am",
                QuickAddTokenKind.PROJECT to "#Finanse",
                QuickAddTokenKind.PRIORITY to "p1",
            ),
            result.tokens.map { it.kind to it.text },
        )
        result.tokens.forEach { assertEquals(it.text, text.substring(it.start, it.end)) }
    }

    /** UX_SPEC §3.4: the chip's cross puts the phrase back into the title. */
    @Test
    fun dismissedPhraseStaysInTheTitle() {
        val text = "Купити хліб завтра о 18"
        val first = QuickAddParser.parse(text, now, projects)
        val date = first.tokens.single { it.kind == QuickAddTokenKind.DATE }
        val second = QuickAddParser.parse(text, now, projects, ignored = listOf(date.range))
        assertEquals("Купити хліб завтра", second.content)
        assertEquals(LocalTime(18, 0), second.time)
        assertEquals(LocalDate(2026, 11, 3), second.date, "18:00 is still ahead today")
    }

    @Test
    fun onlyTheFirstPhraseOfAKindCounts() {
        val result = QuickAddParser.parse("Купити хліб завтра або післязавтра p1 p2", now, projects)
        assertEquals("Купити хліб або післязавтра p2", result.content)
        assertEquals(LocalDate(2026, 11, 4), result.date)
        assertEquals(Priority.P1, result.priority)
    }

    @Test
    fun moreReadingsOfTimesAndDates() {
        fun parse(text: String) = QuickAddParser.parse(text, now, projects)
        assertEquals(LocalTime(15, 0), parse("Зустріч о 3 дня").time)
        assertEquals(LocalTime(19, 0), parse("Dinner at 7 pm").time)
        assertEquals(LocalTime(21, 0), parse("Spotkanie o 9 wieczorem").time)
        assertEquals(LocalTime(23, 0), parse("Дзвінок о 11 ночі").time)
        assertEquals(LocalTime(11, 0), parse("Кава об 11").time)
        assertNull(parse("Тест о 25").time, "no such hour: stays text")
        assertEquals(LocalDate(2028, 2, 29), parse("Party 29.02").date, "the next 29 February")
        assertEquals(LocalDate(2026, 11, 17), parse("Renew in 2 weeks").date)
        val past = parse("Done 31/12/2025")
        assertEquals(LocalDate(2025, 12, 31), past.date, "31 is no month: one reading only")
        assertEquals(setOf(QuickAddWarning.PAST_DATE), past.warnings)
        assertEquals("Rozmowa o pracy", parse("Rozmowa o pracy").content)
        assertEquals("Work at home", parse("Work at home").content)
        assertEquals("Submit report", parse("Submit report tomorrow.").content, "a full stop after the word")
        assertNotNull(parse("Submit report tomorrow.").date)
    }

    /** "The parser never silently changes the text": every typed word is in the title or in a token. */
    @Test
    fun noWordIsEverLost(): Unit = runBlocking {
        val vocabulary = listOf(
            "завтра", "сьогодні", "о", "об", "9", "9:30", "вечора", "у", "п'ятницю", "п’ятницю", "через", "3", "дні", "15.11",
            "15", "листопада", "#Дім", "#Side", "project", "p1", "P2", "p5", "mp3", "tomorrow", "tomorrow,", "today.", "at",
            "9am", "6:30pm", "on", "friday", "next", "mon", "sun", "cream", "in", "days", "Nov", "15th", "03/04", "31/31",
            "29.02", "2027", "the", "day", "after", "jutro", "o", "18", "w", "piątek", "za", "dni", "przyszłym", "tygodniu",
            "#", "##", "p", "24:00", "12pm", "12am", "13pm", "Купити", "хліб", "ivan@today.com", "https://x.y/today",
            "99999999999", "C#", "#Inbox",
        )
        checkAll(2000, Arb.list(Arb.element(vocabulary), 0..9)) { words ->
            val text = words.joinToString(" ")
            val result = QuickAddParser.parse(text, now, projects)
            val covered = result.tokens.flatMap { it.text.split(' ').filter(String::isNotEmpty) }
            val rest = result.content.split(' ').filter(String::isNotEmpty)
            assertEquals(words.sorted(), (covered + rest).sorted(), "\"$text\" -> $result")
            if (result.time != null) assertNotNull(result.date, "a time always comes with a date (R6)")
            assertTrue(result.tokens.zipWithNext().all { (a, b) -> a.end <= b.start }, "tokens overlap in \"$text\"")
        }
    }
}
