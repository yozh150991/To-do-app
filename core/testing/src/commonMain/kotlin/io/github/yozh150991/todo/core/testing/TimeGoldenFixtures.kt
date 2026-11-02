package io.github.yozh150991.todo.core.testing

import io.github.yozh150991.todo.core.model.CivilResolution
import io.github.yozh150991.todo.core.model.CivilTime
import io.github.yozh150991.todo.core.model.CivilTimeOrigin
import io.github.yozh150991.todo.core.model.Due
import io.github.yozh150991.todo.core.model.TimeZones
import io.github.yozh150991.todo.core.model.resolve
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Golden fixtures of the time model (spec §4.0.1), the time-only subset for Ф0: TIME-01..TIME-04 and
 * TIME-08. TIME-05..TIME-07 need the recurrence engine (Ф2) and are added there.
 *
 * Plain checks without kotlin.test, so the very same code runs on the JVM (jvmTest), on Android
 * emulators (androidTest) and in browsers (webApp `?selftest=time`): spec TR-13.
 */
object TimeGoldenFixtures {
    data class Result(val id: String, val passed: Boolean, val details: String) {
        override fun toString(): String = "$id ${if (passed) "pass" else "FAIL"}: $details"
    }

    fun runAll(): List<Result> = listOf(time01(), time02(), time03(), time04(), time08(), allDay())

    private val warsaw get() = TimeZones.of("Europe/Warsaw")
    private val newYork get() = TimeZones.of("America/New_York")

    private fun at(text: String) = Instant.parse(text)

    private fun exact(r: CivilResolution, instant: String) = r is CivilResolution.Exact && r.instant == at(instant)

    private fun check(id: String, block: (MutableList<String>) -> Boolean): Result {
        val notes = mutableListOf<String>()
        return try {
            Result(id, block(notes), notes.joinToString("; "))
        } catch (e: Throwable) {
            Result(id, false, "${notes.joinToString("; ")} threw ${e::class.simpleName}: ${e.message}")
        }
    }

    /** DAILY 02:30 from 2026-03-28 in Europe/Warsaw: 29.03 falls into the DST gap. */
    private fun time01() = check("TIME-01") { notes ->
        val days = listOf(LocalDate(2026, 3, 28), LocalDate(2026, 3, 29), LocalDate(2026, 3, 30))
        val resolved = days.map { CivilTime.resolve(it, LocalTime(2, 30), warsaw, CivilTimeOrigin.GENERATED) }
        resolved.forEach { notes += describe(it) }
        val shifted = resolved[1] as? CivilResolution.ShiftedForward
        exact(resolved[0], "2026-03-28T01:30:00Z") &&
            shifted != null &&
            shifted.instant == at("2026-03-29T01:30:00Z") &&
            shifted.actual == LocalDateTime(2026, 3, 29, 3, 30) &&
            shifted.gap == 1.hours &&
            exact(resolved[2], "2026-03-30T00:30:00Z") &&
            // COUNT=3 counts the shifted occurrence: three distinct instants, none dropped.
            resolved.mapNotNull { it.instantOrNull }.distinct().size == 3
    }

    /** WEEKLY SU 02:30 on 2026-10-25 in Europe/Warsaw: the local time happens twice. */
    private fun time02() = check("TIME-02") { notes ->
        val r = CivilTime.resolve(LocalDate(2026, 10, 25), LocalTime(2, 30), warsaw, CivilTimeOrigin.GENERATED)
        notes += describe(r)
        r is CivilResolution.EarlierOfTwo &&
            r.instant == at("2026-10-25T00:30:00Z") &&
            r.laterInstant == at("2026-10-25T01:30:00Z")
    }

    /** fixed_zone Europe/Warsaw 09:00; the phone moves to America/New_York. */
    private fun time03() = check("TIME-03") { notes ->
        val due = Due(LocalDate(2026, 11, 2), LocalTime(9, 0), "Europe/Warsaw")
        val before = due.resolve(warsaw, CivilTimeOrigin.GENERATED)?.instantOrNull
        val after = due.resolve(newYork, CivilTimeOrigin.GENERATED)?.instantOrNull
        val shownInNewYork = after?.toLocalDateTime(newYork)
        notes += "in Warsaw $before, after travel $after, shown in New York as $shownInNewYork"
        before == at("2026-11-02T08:00:00Z") &&
            after == before &&
            shownInNewYork == LocalDateTime(2026, 11, 2, 3, 0) &&
            due == Due(LocalDate(2026, 11, 2), LocalTime(9, 0), "Europe/Warsaw")
    }

    /** floating_local 09:00; the device changes its zone. */
    private fun time04() = check("TIME-04") { notes ->
        val due = Due(LocalDate(2026, 11, 2), LocalTime(9, 0))
        val inWarsaw = due.resolve(warsaw, CivilTimeOrigin.GENERATED)?.instantOrNull
        val inNewYork = due.resolve(newYork, CivilTimeOrigin.GENERATED)?.instantOrNull
        notes += "device in Warsaw $inWarsaw, device in New York $inNewYork"
        inWarsaw == at("2026-11-02T08:00:00Z") &&
            inNewYork == at("2026-11-02T14:00:00Z") &&
            inNewYork?.toLocalDateTime(newYork) == LocalDateTime(2026, 11, 2, 9, 0) &&
            due == Due(LocalDate(2026, 11, 2), LocalTime(9, 0))
    }

    /** Explicit one-shot 2026-03-29 02:30 in Europe/Warsaw: validation error with choices, no silent shift. */
    private fun time08() = check("TIME-08") { notes ->
        val r = CivilTime.resolve(LocalDate(2026, 3, 29), LocalTime(2, 30), warsaw, CivilTimeOrigin.EXPLICIT)
        notes += describe(r)
        r is CivilResolution.Nonexistent &&
            r.instantOrNull == null &&
            r.gap == 1.hours &&
            r.choices == listOf(LocalDateTime(2026, 3, 29, 3, 0), LocalDateTime(2026, 3, 29, 3, 30))
    }

    /** A date without time is never turned into an instant (no UTC midnight). */
    private fun allDay() = check("ALL-DAY") { notes ->
        val due = Due(LocalDate(2026, 11, 2))
        val r = due.resolve(warsaw, CivilTimeOrigin.EXPLICIT)
        notes += "resolution $r"
        r == null
    }

    private fun describe(r: CivilResolution): String = when (r) {
        is CivilResolution.Exact -> "${r.requested} -> ${r.instant}"
        is CivilResolution.ShiftedForward -> "${r.requested} -> ${r.instant} (gap ${r.gap}, local ${r.actual})"
        is CivilResolution.EarlierOfTwo -> "${r.requested} -> ${r.instant} (later ${r.laterInstant})"
        is CivilResolution.Nonexistent -> "${r.requested} nonexistent (gap ${r.gap}), choices ${r.choices}"
    }
}
