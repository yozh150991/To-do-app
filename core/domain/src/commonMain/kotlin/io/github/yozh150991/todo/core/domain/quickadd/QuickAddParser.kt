package io.github.yozh150991.todo.core.domain.quickadd

import io.github.yozh150991.todo.core.model.Priority
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

/** A project the parser may recognise after `#`. */
data class QuickAddProject(val id: String, val name: String)

enum class QuickAddTokenKind { DATE, TIME, PROJECT, PRIORITY }

/**
 * A recognised phrase, as a range of the typed text: `[start, end)`. The UI highlights it and shows
 * a chip; dismissing the chip passes [range] back in `ignored` and the phrase stays in the title.
 */
data class QuickAddToken(val kind: QuickAddTokenKind, val start: Int, val end: Int, val text: String) {
    val range: IntRange get() = start until end
}

enum class QuickAddWarning {
    /** R5: `03/04` can be March 4 or April 3; [QuickAddResult.alternatives] has the other reading. */
    AMBIGUOUS_DATE,

    /** R6: a time without a date had already passed today, so the task is for tomorrow. */
    TIME_PASSED_TODAY,

    /** R7: an explicitly typed date in the past. */
    PAST_DATE,
}

data class QuickAddResult(
    /** The title: the text without the recognised phrases, spaces collapsed; as typed (trimmed) when nothing is recognised. */
    val content: String,
    val date: LocalDate?,
    val time: LocalTime?,
    val project: QuickAddProject?,
    val priority: Priority?,
    val warnings: Set<QuickAddWarning>,
    /** Other readings of an ambiguous date, besides [date] ([QuickAddWarning.AMBIGUOUS_DATE]). */
    val alternatives: List<LocalDate>,
    val tokens: List<QuickAddToken>,
) {
    val recognisesNothing: Boolean get() = tokens.isEmpty()
}

/**
 * Basic Quick Add (spec §5.4, phase Ф1a; plan PR 6): dates and times in Ukrainian, English and
 * Polish at once, `#project` and `p1`–`p4`. Pure: the caller passes the device's local [now] and
 * the projects; the result says what was recognised and where, and never changes the rest of the
 * text. Rules R1–R11 and the golden phrases live in `core/testing/QuickAddGoldenPhrases`.
 *
 * Each word is tried against every language. A phrase is taken only once per kind: a second date
 * or project stays in the title. Weekdays need their preposition (у/в, on/next/this, w/we), months
 * need a day number, a bare number is never a time — so ordinary words stay text.
 */
object QuickAddParser {

    fun parse(
        text: String,
        now: LocalDateTime,
        projects: List<QuickAddProject> = emptyList(),
        ignored: List<IntRange> = emptyList(),
    ): QuickAddResult = Run(text, now, projects, ignored).parse()

    // --- vocabulary ----------------------------------------------------------------------------

    private val UK_WEEKDAYS = mapOf(
        "понеділок" to 1, "вівторок" to 2, "середу" to 3, "четвер" to 4, "п'ятницю" to 5, "суботу" to 6, "неділю" to 7,
    )
    private val PL_WEEKDAYS = mapOf(
        "poniedziałek" to 1, "wtorek" to 2, "środę" to 3, "czwartek" to 4, "piątek" to 5, "sobotę" to 6, "niedzielę" to 7,
    )
    private val EN_WEEKDAYS = mapOf(
        "monday" to 1, "tuesday" to 2, "wednesday" to 3, "thursday" to 4, "friday" to 5, "saturday" to 6, "sunday" to 7,
    )

    /** R9: short forms only after "next" / "this" — "on sun cream" is not Sunday. */
    private val EN_WEEKDAYS_SHORT = EN_WEEKDAYS + mapOf(
        "mon" to 1, "tue" to 2, "tues" to 2, "wed" to 3, "thu" to 4, "thur" to 4, "thurs" to 4,
        "fri" to 5, "sat" to 6, "sun" to 7,
    )

    private val WEEKDAY_PREPOSITIONS = mapOf(
        "у" to UK_WEEKDAYS, "в" to UK_WEEKDAYS,
        "on" to EN_WEEKDAYS, "next" to EN_WEEKDAYS_SHORT, "this" to EN_WEEKDAYS_SHORT,
        "w" to PL_WEEKDAYS, "we" to PL_WEEKDAYS,
    )

    private val UK_MONTHS = listOf(
        "січня", "лютого", "березня", "квітня", "травня", "червня", "липня", "серпня", "вересня", "жовтня", "листопада", "грудня",
    )
    private val PL_MONTHS = listOf(
        "stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca", "lipca", "sierpnia", "września", "października", "listopada", "grudnia",
    )
    private val EN_MONTHS: Map<String, Int> = listOf(
        listOf("january", "jan"), listOf("february", "feb"), listOf("march", "mar"), listOf("april", "apr"), listOf("may"),
        listOf("june", "jun"), listOf("july", "jul"), listOf("august", "aug"), listOf("september", "sep", "sept"),
        listOf("october", "oct"), listOf("november", "nov"), listOf("december", "dec"),
    ).flatMapIndexed { index, names -> names.map { it to index + 1 } }.toMap()

    /** Month names that follow a day number ("15 листопада", "15 listopada", "15 Nov"). */
    private val DAY_MONTH: Map<String, Int> =
        UK_MONTHS.mapIndexed { i, m -> m to i + 1 }.toMap() + PL_MONTHS.mapIndexed { i, m -> m to i + 1 }.toMap() + EN_MONTHS

    private val RELATIVE_DAYS = mapOf(
        "сьогодні" to 0, "завтра" to 1, "післязавтра" to 2,
        "today" to 0, "tomorrow" to 1,
        "dziś" to 0, "dzisiaj" to 0, "jutro" to 1, "pojutrze" to 2,
    )

    private const val NEXT_WEEK = -1

    /** Multi-word phrases → days from today, or [NEXT_WEEK] (the next Monday, R3). Tried in this order. */
    private val PHRASES: List<Pair<List<String>, Int>> = listOf(
        listOf("the", "day", "after", "tomorrow") to 2,
        listOf("day", "after", "tomorrow") to 2,
        listOf("наступного", "тижня") to NEXT_WEEK,
        listOf("next", "week") to NEXT_WEEK,
        listOf("w", "przyszłym", "tygodniu") to NEXT_WEEK,
        listOf("через", "тиждень") to 7,
        listOf("in", "a", "week") to 7,
        listOf("za", "tydzień") to 7,
    )

    private val IN_N = setOf("через", "in", "za")
    private val DAY_UNITS = setOf("день", "дні", "днів", "дня", "day", "days", "dzień", "dni")
    private val WEEK_UNITS = setOf("тиждень", "тижні", "тижнів", "week", "weeks", "tydzień", "tygodnie", "tygodni")

    /** R10: the Latin "o" typed instead of the Cyrillic "о" is the same word. */
    private val TIME_PREPOSITIONS = setOf("о", "об", "o", "at")

    private enum class Period { AM, PM, NIGHT }

    private val PERIODS = mapOf(
        "am" to Period.AM, "a.m." to Period.AM, "pm" to Period.PM, "p.m." to Period.PM,
        "ранку" to Period.AM, "вечора" to Period.PM, "дня" to Period.PM, "ночі" to Period.NIGHT,
        "rano" to Period.AM, "wieczorem" to Period.PM,
    )
    private val EN_PERIOD_WORDS = setOf("am", "pm", "a.m.", "p.m.")

    private val CLOCK = Regex("""^(\d{1,2})(?::(\d{2}))?(am|pm|a\.m\.|p\.m\.)?$""")
    private val DOTTED = Regex("""^(\d{1,2})\.(\d{1,2})(?:\.(\d{4}))?\.?$""")
    private val SLASHED = Regex("""^(\d{1,2})/(\d{1,2})(?:/(\d{4}))?$""")
    private val DAY_NUMBER = Regex("""^(\d{1,2})(?:st|nd|rd|th)?$""")
    private val YEAR = Regex("""^\d{4}$""")
    private val PRIORITY = Regex("""^p([1-4])$""")
    private val WORD = Regex("""\S+""")
    private const val TRAILING = ",;!?"

    /** Upper bound for "in N days/weeks", so absurd numbers stay text instead of overflowing dates. */
    private const val MAX_DAYS = 3660

    // --- helpers -------------------------------------------------------------------------------

    /** Lower case, typographic apostrophes as `'`, trailing `,;!?` removed. */
    private fun normalize(word: String): String =
        word.lowercase().replace('’', '\'').replace('ʼ', '\'').trimEnd(*TRAILING.toCharArray())

    private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate(year, month, day) }.getOrNull()

    /** R4: a day and month without a year is the nearest such date from [today] on. */
    private fun nearestDayMonth(today: LocalDate, month: Int, day: Int): LocalDate? {
        dateOrNull(today.year, month, day)?.let { if (it >= today) return it }
        // 29 February: the next year that has it.
        for (year in today.year + 1..today.year + 8) dateOrNull(year, month, day)?.let { return it }
        return null
    }

    private fun parseClock(text: String, period: Period? = null): LocalTime? {
        val m = CLOCK.matchEntire(text) ?: return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
        val effective = m.groupValues[3].ifEmpty { null }?.let { PERIODS[it] } ?: period
        if (minute > 59) return null
        when (effective) {
            null -> if (hour > 23) return null
            else -> {
                if (hour !in 1..12) return null
                hour = when (effective) {
                    Period.AM -> if (hour == 12) 0 else hour
                    Period.PM -> if (hour == 12) 12 else hour + 12
                    // "о 11 ночі" is 23:00, "о 2 ночі" is 02:00.
                    Period.NIGHT -> if (hour in 9..11) hour + 12 else if (hour == 12) 0 else hour
                }
            }
        }
        return LocalTime(hour, minute)
    }

    private class Word(val start: Int, val end: Int, val raw: String) {
        val norm: String = normalize(raw)

        /** [norm] and, when it ends with a full stop, also without it ("tomorrow." at the end of a sentence). */
        val candidates: List<String> = if (norm.length > 1 && norm.endsWith('.')) listOf(norm, norm.dropLast(1)) else listOf(norm)
    }

    private class Run(
        val text: String,
        val now: LocalDateTime,
        val projects: List<QuickAddProject>,
        val ignored: List<IntRange>,
    ) {
        val today: LocalDate = now.date
        val words: List<Word> = WORD.findAll(text).map { Word(it.range.first, it.range.last + 1, it.value) }.toList()
        val consumed = BooleanArray(words.size)
        val tokens = mutableListOf<QuickAddToken>()
        var date: LocalDate? = null
        var time: LocalTime? = null
        var project: QuickAddProject? = null
        var priority: Priority? = null
        val warnings = mutableSetOf<QuickAddWarning>()
        var alternatives: List<LocalDate> = emptyList()

        fun norm(i: Int): String? = words.getOrNull(i)?.norm

        fun candidates(i: Int): List<String> = words.getOrNull(i)?.candidates.orEmpty()

        /** Words `[i, j)` exist, are not taken yet and do not touch a range the user dismissed. */
        fun free(i: Int, j: Int): Boolean {
            if (j > words.size || (i until j).any { consumed[it] }) return false
            val start = words[i].start
            val end = words[j - 1].end
            return ignored.none { it.first < end && start < it.last + 1 }
        }

        fun take(kind: QuickAddTokenKind, i: Int, j: Int) {
            for (k in i until j) consumed[k] = true
            val start = words[i].start
            val end = words[j - 1].end
            tokens += QuickAddToken(kind, start, end, text.substring(start, end))
        }

        fun setDate(i: Int, j: Int, value: LocalDate?, warning: Set<QuickAddWarning> = emptySet(), others: List<LocalDate> = emptyList()): Boolean {
            if (date != null || value == null || !free(i, j)) return false
            date = value
            warnings += warning
            alternatives = others
            take(QuickAddTokenKind.DATE, i, j)
            return true
        }

        fun setTime(i: Int, j: Int, value: LocalTime?): Boolean {
            if (time != null || value == null || !free(i, j)) return false
            time = value
            take(QuickAddTokenKind.TIME, i, j)
            return true
        }

        /** R7: an explicit year in the past. */
        fun pastIfExplicit(value: LocalDate?): Set<QuickAddWarning> =
            if (value != null && value < today) setOf(QuickAddWarning.PAST_DATE) else emptySet()

        fun parse(): QuickAddResult {
            for (i in words.indices) {
                if (consumed[i]) continue
                matchPhrase(i) || matchRelative(i) || matchWeekday(i) || matchInN(i) || matchNumeric(i) ||
                    matchDayMonth(i) || matchTime(i) || matchProject(i) || matchPriority(i)
            }
            val timeValue = time
            if (timeValue != null && date == null) {
                // R6: today while the time is still ahead, otherwise tomorrow — and the preview says so.
                if (timeValue > now.time) {
                    date = today
                } else {
                    date = today.plus(1, DateTimeUnit.DAY)
                    warnings += QuickAddWarning.TIME_PASSED_TODAY
                }
            }
            val content = if (consumed.any { it }) {
                words.filterIndexed { k, _ -> !consumed[k] }.joinToString(" ") { it.raw }
            } else {
                text.trim()
            }
            return QuickAddResult(content, date, time, project, priority, warnings.toSet(), alternatives, tokens.sortedBy { it.start })
        }

        fun matchPhrase(i: Int): Boolean {
            for ((phrase, value) in PHRASES) {
                val j = i + phrase.size
                if (j > words.size || !phrase.indices.all { phrase[it] in candidates(i + it) }) continue
                val target = if (value == NEXT_WEEK) nextMonday() else today.plus(value, DateTimeUnit.DAY)
                if (setDate(i, j, target)) return true
            }
            return false
        }

        /** R3: weeks start on Monday; "next week" is the next Monday after today. */
        fun nextMonday(): LocalDate {
            val delta = (8 - today.dayOfWeek.isoDayNumber).let { if (it == 0) 7 else it }
            return today.plus(delta, DateTimeUnit.DAY)
        }

        fun matchRelative(i: Int): Boolean {
            val days = candidates(i).firstNotNullOfOrNull { RELATIVE_DAYS[it] } ?: return false
            return setDate(i, i + 1, today.plus(days, DateTimeUnit.DAY))
        }

        /** R2: the nearest such day after today; today's weekday means next week. */
        fun matchWeekday(i: Int): Boolean {
            val forms = WEEKDAY_PREPOSITIONS[norm(i)] ?: return false
            if (i + 1 >= words.size) return false
            val day = candidates(i + 1).firstNotNullOfOrNull { forms[it] } ?: return false
            val delta = ((day - today.dayOfWeek.isoDayNumber) % 7 + 7) % 7
            return setDate(i, i + 2, today.plus(if (delta == 0) 7 else delta, DateTimeUnit.DAY))
        }

        fun matchInN(i: Int): Boolean {
            if (norm(i) !in IN_N || i + 2 >= words.size) return false
            val n = norm(i + 1)?.takeIf { s -> s.isNotEmpty() && s.all { it.isDigit() } }?.toIntOrNull() ?: return false
            if (n > MAX_DAYS) return false
            for (unit in candidates(i + 2)) {
                val days = when (unit) {
                    in DAY_UNITS -> n
                    in WEEK_UNITS -> n * 7
                    else -> continue
                }
                if (days > MAX_DAYS) return false
                return setDate(i, i + 3, today.plus(days, DateTimeUnit.DAY))
            }
            return false
        }

        fun matchNumeric(i: Int): Boolean {
            val word = norm(i) ?: return false
            DOTTED.matchEntire(word)?.let { m ->
                // R5: dots are day.month in every language.
                val day = m.groupValues[1].toInt()
                val month = m.groupValues[2].toInt()
                val year = m.groupValues[3].ifEmpty { null }?.toInt()
                val value = if (year != null) dateOrNull(year, month, day) else nearestDayMonth(today, month, day)
                return setDate(i, i + 1, value, if (year != null) pastIfExplicit(value) else emptySet())
            }
            SLASHED.matchEntire(word)?.let { m ->
                // R5: a slash is ambiguous — month/day first (as in the US), day/month offered too.
                val a = m.groupValues[1].toInt()
                val b = m.groupValues[2].toInt()
                val year = m.groupValues[3].ifEmpty { null }?.toInt()
                fun read(month: Int, day: Int) = if (year != null) dateOrNull(year, month, day) else nearestDayMonth(today, month, day)
                val us = read(a, b)
                val eu = read(b, a)
                if (us != null && eu != null && us != eu) {
                    val past = if (year != null) pastIfExplicit(us) else emptySet()
                    return setDate(i, i + 1, us, setOf(QuickAddWarning.AMBIGUOUS_DATE) + past, listOf(eu))
                }
                val value = us ?: eu
                return setDate(i, i + 1, value, if (year != null) pastIfExplicit(value) else emptySet())
            }
            return false
        }

        fun matchDayMonth(i: Int): Boolean {
            fun yearAt(k: Int): Int? = norm(k)?.takeIf { YEAR.matches(it) }?.toInt()

            // "15 листопада", "15 listopada", "15 Nov", optionally followed by a year.
            val dayFirst = DAY_NUMBER.matchEntire(norm(i) ?: "")
            if (dayFirst != null && i + 1 < words.size) {
                val month = candidates(i + 1).firstNotNullOfOrNull { DAY_MONTH[it] }
                if (month != null) {
                    val day = dayFirst.groupValues[1].toInt()
                    val year = yearAt(i + 2)
                    val value = if (year != null) dateOrNull(year, month, day) else nearestDayMonth(today, month, day)
                    return setDate(i, if (year != null) i + 3 else i + 2, value, if (year != null) pastIfExplicit(value) else emptySet())
                }
            }
            // "Nov 15", "January 3rd", optionally followed by a year.
            val month = candidates(i).firstNotNullOfOrNull { EN_MONTHS[it] } ?: return false
            if (i + 1 >= words.size) return false
            val dayAfter = DAY_NUMBER.matchEntire(norm(i + 1) ?: "") ?: return false
            val day = dayAfter.groupValues[1].toInt()
            val year = yearAt(i + 2)
            val value = if (year != null) dateOrNull(year, month, day) else nearestDayMonth(today, month, day)
            return setDate(i, if (year != null) i + 3 else i + 2, value, if (year != null) pastIfExplicit(value) else emptySet())
        }

        fun matchTime(i: Int): Boolean {
            val word = norm(i) ?: return false
            if (word in TIME_PREPOSITIONS) {
                if (i + 1 >= words.size) return false
                val clock = norm(i + 1) ?: return false
                val period = norm(i + 2)?.let { PERIODS[it] }
                val clockMatch = CLOCK.matchEntire(clock)
                if (period != null && clockMatch != null && clockMatch.groupValues[3].isEmpty()) {
                    val value = parseClock(clock, period)
                    if (value != null) return setTime(i, i + 3, value)
                }
                val value = parseClock(clock) ?: return false
                return setTime(i, i + 2, value)
            }
            val m = CLOCK.matchEntire(word) ?: return false
            val next = norm(i + 1)
            if (m.groupValues[3].isEmpty() && next != null && next in EN_PERIOD_WORDS) {
                val value = parseClock(word, PERIODS.getValue(next)) ?: return false
                return setTime(i, i + 2, value)
            }
            // "9am", "6:30pm", "18:30" — never a bare number (R8).
            if (m.groupValues[3].isNotEmpty() || m.groupValues[2].isNotEmpty()) return setTime(i, i + 1, parseClock(word))
            return false
        }

        /** R8: `#` at a word start followed by an existing project name; the longest name wins. */
        fun matchProject(i: Int): Boolean {
            val start = words[i].start
            if (project != null || !text.startsWith("#", start)) return false
            val rest = text.substring(start + 1)
            for (candidate in projects.sortedByDescending { it.name.length }) {
                val name = candidate.name
                if (name.isEmpty() || !rest.regionMatches(0, name, 0, name.length, ignoreCase = true)) continue
                val after = rest.getOrNull(name.length)
                if (after != null && !after.isWhitespace() && after !in "$TRAILING.") continue
                val end = start + 1 + name.length
                var j = i
                while (j < words.size && words[j].start < end) j++
                if (free(i, j)) {
                    project = candidate
                    take(QuickAddTokenKind.PROJECT, i, j)
                    return true
                }
            }
            return false
        }

        fun matchPriority(i: Int): Boolean {
            val m = PRIORITY.matchEntire(norm(i) ?: return false) ?: return false
            if (priority != null || !free(i, i + 1)) return false
            priority = Priority.fromLevel(m.groupValues[1].toInt())
            take(QuickAddTokenKind.PRIORITY, i, i + 1)
            return true
        }
    }
}
