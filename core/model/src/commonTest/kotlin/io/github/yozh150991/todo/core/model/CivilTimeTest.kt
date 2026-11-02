package io.github.yozh150991.todo.core.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Edge cases of the civil-time resolver beyond the golden fixtures (core/testing TimeGoldenFixtures). */
class CivilTimeTest {

    @Test
    fun zoneWithoutDstIsAlwaysExact() {
        val r = CivilTime.resolve(LocalDate(2026, 3, 29), LocalTime(2, 30), TimeZones.of("Asia/Kolkata"), CivilTimeOrigin.EXPLICIT)
        assertIs<CivilResolution.Exact>(r)
        assertEquals(Instant.parse("2026-03-28T21:00:00Z"), r.instant)
    }

    @Test
    fun halfHourGapOnLordHowe() {
        // Lord Howe Island moves its clocks by 30 minutes: 02:00 -> 02:30 on 2026-10-04.
        val zone = TimeZones.of("Australia/Lord_Howe")
        val generated = CivilTime.resolve(LocalDate(2026, 10, 4), LocalTime(2, 15), zone, CivilTimeOrigin.GENERATED)
        assertIs<CivilResolution.ShiftedForward>(generated)
        assertEquals(30.minutes, generated.gap)
        assertEquals(LocalDateTime(2026, 10, 4, 2, 45), generated.actual)

        val explicit = CivilTime.resolve(LocalDate(2026, 10, 4), LocalTime(2, 15), zone, CivilTimeOrigin.EXPLICIT)
        assertIs<CivilResolution.Nonexistent>(explicit)
        assertEquals(listOf(LocalDateTime(2026, 10, 4, 2, 30), LocalDateTime(2026, 10, 4, 2, 45)), explicit.choices)
    }

    @Test
    fun wholeSkippedDayInSamoa() {
        // Samoa skipped 2011-12-30 entirely when it moved across the date line.
        val zone = TimeZones.of("Pacific/Apia")
        val r = CivilTime.resolve(LocalDate(2011, 12, 30), LocalTime(9, 0), zone, CivilTimeOrigin.GENERATED)
        assertIs<CivilResolution.ShiftedForward>(r)
        assertEquals(24.hours, r.gap)
        assertEquals(LocalDateTime(2011, 12, 31, 9, 0), r.actual)
    }

    @Test
    fun dayBeforeTheGapIsNotAffected() {
        val r = CivilTime.resolve(LocalDate(2026, 3, 28), LocalTime(2, 30), TimeZones.of("Europe/Warsaw"), CivilTimeOrigin.EXPLICIT)
        assertIs<CivilResolution.Exact>(r)
        assertEquals(Instant.parse("2026-03-28T01:30:00Z"), r.instant)
    }

    @Test
    fun allDayDueHasNoInstant() {
        assertNull(Due(LocalDate(2026, 11, 2)).resolve(TimeZones.of("Europe/Warsaw"), CivilTimeOrigin.EXPLICIT))
    }
}
