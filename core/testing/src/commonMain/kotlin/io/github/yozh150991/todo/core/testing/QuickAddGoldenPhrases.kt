package io.github.yozh150991.todo.core.testing

import io.github.yozh150991.todo.core.model.Priority
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * Golden phrases of the basic Quick Add (spec §5.4, phase Ф1a; plan PR 6): what the parser must
 * recognise in Ukrainian, English and Polish, and — just as important — what must stay plain text.
 * Written before the parser so its rules are reviewed as data first (spec §12.2: test design).
 *
 * The parser gets no language hint: every phrase is parsed against all three languages at once,
 * whatever the interface language (owner's decision, 03.11.2026).
 *
 * Ф1a scope: dates and times, `#project`, `p1`–`p4`. Recurrence, `/section`, `%label`, `{deadline}`,
 * `!reminder`, durations and `//` descriptions are Ф2; phrases that use them stay text in Ф1a.
 *
 * Rules the cases pin down (change them here first, then in the parser):
 * - R1. The reference moment is [now] in [zone]: Tuesday, 3 November 2026, 10:00, Europe/Warsaw.
 * - R2. A weekday name means the nearest such day after today; today itself is "today"
 *   ("on Tuesday" said on a Tuesday is next week's Tuesday).
 * - R3. "Next week" is the next Monday (weeks start on Monday in all three languages).
 * - R4. A day and month without a year is the nearest such date from today on; a date already
 *   passed this year moves to next year.
 * - R5. `DD.MM` with dots is day.month in every language. `03/04` with a slash is ambiguous
 *   (American month/day versus day/month): the preview offers both.
 * - R6. A time without a date is today if still ahead, otherwise tomorrow, and the preview says so.
 * - R7. An explicit date in the past is kept, with a warning in the preview.
 * - R8. Words are matched whole: no dates inside other words, e-mail addresses or URLs; a bare
 *   number is not a time; `#` and `p1`–`p4` count only at a word start; `#name` must be an
 *   existing project (longest name wins), otherwise it stays text.
 * - R9. Short English weekday forms (mon … sun) count only after "on", "next" or "this":
 *   "sun cream" is not Sunday.
 * - R10. A Latin "o" typed instead of the Cyrillic "о" ("o 9") still means "at".
 * - R11. Recognised words are removed from the title together with their preposition
 *   ("у п'ятницю", "on Friday", "w piątek", "at 9am"); the rest keeps its spelling, spaces collapsed.
 */
object QuickAddGoldenPhrases {
    enum class Language { UK, EN, PL }

    enum class Warning {
        /** R5: the date can be read two ways; the preview offers [Expectation.alternatives]. */
        AMBIGUOUS_DATE,

        /** R6: a time without a date that has already passed today moved to tomorrow. */
        TIME_PASSED_TODAY,

        /** R7: an explicit date in the past. */
        PAST_DATE,
    }

    data class Expectation(
        val content: String,
        val date: LocalDate? = null,
        val time: LocalTime? = null,
        val project: String? = null,
        val priority: Priority? = null,
        val warning: Warning? = null,
        /** Other readings the preview offers for [Warning.AMBIGUOUS_DATE], besides [date]. */
        val alternatives: List<LocalDate> = emptyList(),
    ) {
        val recognisesNothing: Boolean get() = date == null && time == null && project == null && priority == null
    }

    data class Case(val id: String, val language: Language, val input: String, val expected: Expectation, val rule: String = "")

    /** R1. */
    val now: LocalDateTime = LocalDateTime(2026, 11, 3, 10, 0)
    const val zone: String = "Europe/Warsaw"

    /** Projects that exist when the phrases are parsed (R8). */
    val projects: List<String> = listOf("Inbox", "Робота", "Дім", "Finanse", "Side project")

    private val today = LocalDate(2026, 11, 3)
    private val tomorrow = LocalDate(2026, 11, 4)
    private val dayAfterTomorrow = LocalDate(2026, 11, 5)
    private val friday = LocalDate(2026, 11, 6)
    private val inThreeDays = LocalDate(2026, 11, 6)
    private val nextMonday = LocalDate(2026, 11, 9)
    private val nextTuesday = LocalDate(2026, 11, 10)
    private val nov15 = LocalDate(2026, 11, 15)
    private val jan3 = LocalDate(2027, 1, 3)

    private fun t(hour: Int, minute: Int = 0) = LocalTime(hour, minute)

    private fun case(language: Language, n: Int, input: String, expected: Expectation, rule: String) =
        Case("QA-${language.name}-${n.toString().padStart(2, '0')}", language, input, expected, rule)

    private fun uk(n: Int, input: String, expected: Expectation, rule: String = "") = case(Language.UK, n, input, expected, rule)

    private fun en(n: Int, input: String, expected: Expectation, rule: String = "") = case(Language.EN, n, input, expected, rule)

    private fun pl(n: Int, input: String, expected: Expectation, rule: String = "") = case(Language.PL, n, input, expected, rule)

    val all: List<Case> = listOf(
        // --- Ukrainian ---------------------------------------------------------------------------
        uk(1, "Купити хліб сьогодні", Expectation("Купити хліб", today)),
        uk(2, "Подзвонити мамі завтра о 18", Expectation("Подзвонити мамі", tomorrow, t(18))),
        uk(3, "Забрати посилку післязавтра", Expectation("Забрати посилку", dayAfterTomorrow)),
        uk(4, "Здати звіт у п'ятницю", Expectation("Здати звіт", friday), "R2, R11"),
        uk(5, "Здати звіт у п’ятницю", Expectation("Здати звіт", friday), "typographic apostrophe"),
        uk(6, "Нарада у вівторок о 9:30", Expectation("Нарада", nextTuesday, t(9, 30)), "R2: today is Tuesday"),
        uk(7, "Оплатити інтернет 15.11", Expectation("Оплатити інтернет", nov15), "R5"),
        uk(8, "Оплатити інтернет 15 листопада", Expectation("Оплатити інтернет", nov15)),
        uk(9, "Записатися до лікаря через 3 дні", Expectation("Записатися до лікаря", inThreeDays)),
        uk(10, "Спланувати відпустку наступного тижня", Expectation("Спланувати відпустку", nextMonday), "R3"),
        uk(11, "Подати декларацію 3 січня", Expectation("Подати декларацію", jan3), "R4: next year"),
        uk(12, "Вигуляти собаку о 9", Expectation("Вигуляти собаку", tomorrow, t(9), warning = Warning.TIME_PASSED_TODAY), "R6"),
        uk(13, "Полити квіти о 18:30", Expectation("Полити квіти", today, t(18, 30)), "R6"),
        uk(14, "Зустріч завтра о 9 вечора", Expectation("Зустріч", tomorrow, t(21))),
        uk(15, "Зустріч завтра o 9", Expectation("Зустріч", tomorrow, t(9)), "R10: Latin o"),
        uk(16, "Зробити бекап #Робота p1", Expectation("Зробити бекап", project = "Робота", priority = Priority.P1)),
        uk(17, "Прибрати балкон #Дім завтра p3", Expectation("Прибрати балкон", tomorrow, project = "Дім", priority = Priority.P3)),
        uk(18, "Сплатити внесок 15.01.2026", Expectation("Сплатити внесок", LocalDate(2026, 1, 15), warning = Warning.PAST_DATE), "R7"),
        // Plain text: nothing is recognised.
        uk(19, "Купити 9 яблук", Expectation("Купити 9 яблук"), "R8: a bare number is not a time"),
        uk(20, "Розібрати сьогоднішню пошту", Expectation("Розібрати сьогоднішню пошту"), "R8: whole words only"),
        uk(21, "Подзвонити #Банк", Expectation("Подзвонити #Банк"), "R8: no such project"),
        uk(22, "Вивчити основи C#", Expectation("Вивчити основи C#"), "R8: # inside a word"),
        uk(23, "Конвертувати mp3 у flac", Expectation("Конвертувати mp3 у flac"), "R8: p3 inside a word"),
        uk(24, "Перевірити p5", Expectation("Перевірити p5"), "only p1–p4"),
        uk(25, "Щопонеділка винести сміття", Expectation("Щопонеділка винести сміття"), "recurrence is Ф2"),

        // --- English -----------------------------------------------------------------------------
        en(1, "Buy bread today", Expectation("Buy bread", today)),
        en(2, "Pay rent tomorrow at 9am #Finanse p1", Expectation("Pay rent", tomorrow, t(9), "Finanse", Priority.P1)),
        en(3, "Call John today 6:30pm", Expectation("Call John", today, t(18, 30))),
        en(4, "Submit report on Friday", Expectation("Submit report", friday), "R2, R11"),
        en(5, "Standup on Tuesday at 9:30", Expectation("Standup", nextTuesday, t(9, 30)), "R2: today is Tuesday"),
        en(6, "Plan the trip next week", Expectation("Plan the trip", nextMonday), "R3"),
        en(7, "Renew passport in 3 days", Expectation("Renew passport", inThreeDays)),
        en(8, "Dentist Nov 15 at 10", Expectation("Dentist", nov15, t(10))),
        en(9, "Book tickets 15 Nov", Expectation("Book tickets", nov15)),
        en(10, "File taxes Jan 3", Expectation("File taxes", jan3), "R4: next year"),
        en(11, "Water plants at 21:00", Expectation("Water plants", today, t(21)), "R6"),
        en(12, "Walk the dog at 8am", Expectation("Walk the dog", tomorrow, t(8), warning = Warning.TIME_PASSED_TODAY), "R6"),
        en(13, "Pick up parcel the day after tomorrow", Expectation("Pick up parcel", dayAfterTomorrow)),
        en(14, "Review #Side project PR next mon", Expectation("Review PR", nextMonday, project = "Side project"), "R8 longest project, R9"),
        en(15, "Meeting 03/04", Expectation("Meeting", LocalDate(2027, 3, 4), warning = Warning.AMBIGUOUS_DATE, alternatives = listOf(LocalDate(2027, 4, 3))), "R5"),
        en(16, "Meeting 03.04", Expectation("Meeting", LocalDate(2027, 4, 3)), "R5: dots are day.month"),
        // Plain text.
        en(17, "Put on sun cream", Expectation("Put on sun cream"), "R9"),
        en(18, "Ask if we may park here", Expectation("Ask if we may park here"), "R8: 'may' is not a month here"),
        en(19, "Email ivan@today.com", Expectation("Email ivan@today.com"), "R8: inside an e-mail"),
        en(20, "Read https://example.com/tomorrow", Expectation("Read https://example.com/tomorrow"), "R8: inside a URL"),
        en(21, "Buy 2 lemons", Expectation("Buy 2 lemons"), "R8: 'mon' inside a word, bare number"),
        en(22, "Water plants every Monday", Expectation("Water plants every Monday"), "recurrence is Ф2"),

        // --- Polish ------------------------------------------------------------------------------
        pl(1, "Kupić chleb dziś", Expectation("Kupić chleb", today)),
        pl(2, "Zadzwonić do mamy jutro o 18", Expectation("Zadzwonić do mamy", tomorrow, t(18))),
        pl(3, "Odebrać paczkę pojutrze", Expectation("Odebrać paczkę", dayAfterTomorrow)),
        pl(4, "Oddać raport w piątek", Expectation("Oddać raport", friday), "R2, R11"),
        pl(5, "Spotkanie we wtorek o 9:30", Expectation("Spotkanie", nextTuesday, t(9, 30)), "R2: today is Tuesday"),
        pl(6, "Zaplanować urlop w przyszłym tygodniu", Expectation("Zaplanować urlop", nextMonday), "R3"),
        pl(7, "Zapisać się do lekarza za 3 dni", Expectation("Zapisać się do lekarza", inThreeDays)),
        pl(8, "Kupić prezent 15 listopada", Expectation("Kupić prezent", nov15)),
        pl(9, "Zapłacić czynsz 15.11 #Finanse p2", Expectation("Zapłacić czynsz", nov15, project = "Finanse", priority = Priority.P2)),
        pl(10, "Złożyć PIT 3 stycznia", Expectation("Złożyć PIT", jan3), "R4: next year"),
        pl(11, "Podlać kwiaty dziś o 21:30", Expectation("Podlać kwiaty", today, t(21, 30))),
        pl(12, "Wyprowadzić psa o 8", Expectation("Wyprowadzić psa", tomorrow, t(8), warning = Warning.TIME_PASSED_TODAY), "R6"),
        pl(13, "Spotkanie 03.04", Expectation("Spotkanie", LocalDate(2027, 4, 3)), "R5"),
        // Plain text.
        pl(14, "Przeczytać dzisiejszą gazetę", Expectation("Przeczytać dzisiejszą gazetę"), "R8: whole words only"),
        pl(15, "Kupić 5 jabłek", Expectation("Kupić 5 jabłek"), "R8: a bare number is not a time"),
        pl(16, "Zadzwonić do #Urząd", Expectation("Zadzwonić do #Urząd"), "R8: no such project"),
        pl(17, "Co poniedziałek wynieść śmieci", Expectation("Co poniedziałek wynieść śmieci"), "recurrence is Ф2"),
    )

    /** Structural checks of the table itself; the parser tests (Ф1a PR 6) use [all]. */
    fun problems(): List<String> = buildList {
        all.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { add("duplicate id $it") }
        Language.entries.forEach { language ->
            val cases = all.filter { it.language == language }
            if (cases.size < 15) add("$language: only ${cases.size} cases")
            if (cases.none { it.expected.recognisesNothing }) add("$language: no plain-text cases")
            if (cases.none { it.expected.project != null }) add("$language: no #project case")
            if (cases.none { it.expected.priority != null }) add("$language: no priority case")
            if (cases.none { it.expected.time != null }) add("$language: no time case")
        }
        all.forEach { case ->
            val e = case.expected
            if (e.content.isBlank()) add("${case.id}: blank content")
            if (e.content != e.content.trim() || "  " in e.content) add("${case.id}: content not normalised")
            if (e.time != null && e.date == null) add("${case.id}: a time needs a date (R6)")
            if (e.project != null && e.project !in projects) add("${case.id}: unknown project ${e.project}")
            if ((e.warning == Warning.AMBIGUOUS_DATE) != e.alternatives.isNotEmpty()) {
                add("${case.id}: alternatives only and always with AMBIGUOUS_DATE")
            }
            val date = e.date
            if (date != null) {
                val inPast = date < today
                if (inPast != (e.warning == Warning.PAST_DATE)) add("${case.id}: past dates need PAST_DATE and only they")
            }
            if (e.warning == Warning.TIME_PASSED_TODAY) {
                val passed = e.time != null && e.time < now.time
                if (!passed || e.date != tomorrow) add("${case.id}: TIME_PASSED_TODAY needs a passed time moved to tomorrow")
            }
            if (e.recognisesNothing && e.content != case.input) add("${case.id}: plain text must stay exactly as typed")
        }
    }
}
