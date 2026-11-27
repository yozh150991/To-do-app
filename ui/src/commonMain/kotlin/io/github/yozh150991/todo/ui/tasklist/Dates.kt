package io.github.yozh150991.todo.ui.tasklist

import androidx.compose.runtime.Composable
import io.github.yozh150991.todo.core.model.Due
import io.github.yozh150991.todo.core.model.TimeZones
import io.github.yozh150991.todo.ui.resources.Res
import io.github.yozh150991.todo.ui.resources.date_day_month
import io.github.yozh150991.todo.ui.resources.date_day_month_year
import io.github.yozh150991.todo.ui.resources.date_today
import io.github.yozh150991.todo.ui.resources.date_tomorrow
import io.github.yozh150991.todo.ui.resources.date_yesterday
import io.github.yozh150991.todo.ui.resources.deadline_days
import io.github.yozh150991.todo.ui.resources.deadline_passed
import io.github.yozh150991.todo.ui.resources.deadline_today
import io.github.yozh150991.todo.ui.resources.month_1
import io.github.yozh150991.todo.ui.resources.month_10
import io.github.yozh150991.todo.ui.resources.month_11
import io.github.yozh150991.todo.ui.resources.month_12
import io.github.yozh150991.todo.ui.resources.month_2
import io.github.yozh150991.todo.ui.resources.month_3
import io.github.yozh150991.todo.ui.resources.month_4
import io.github.yozh150991.todo.ui.resources.month_5
import io.github.yozh150991.todo.ui.resources.month_6
import io.github.yozh150991.todo.ui.resources.month_7
import io.github.yozh150991.todo.ui.resources.month_8
import io.github.yozh150991.todo.ui.resources.month_9
import io.github.yozh150991.todo.ui.resources.weekday_1
import io.github.yozh150991.todo.ui.resources.weekday_2
import io.github.yozh150991.todo.ui.resources.weekday_3
import io.github.yozh150991.todo.ui.resources.weekday_4
import io.github.yozh150991.todo.ui.resources.weekday_5
import io.github.yozh150991.todo.ui.resources.weekday_6
import io.github.yozh150991.todo.ui.resources.weekday_7
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * How a date reads next to a task (UX_SPEC §8): "Today", "Tomorrow", "Yesterday", a weekday within
 * the coming week, otherwise a short date — with the year only when it is not this year.
 */
sealed interface DayLabel {
    data object Today : DayLabel
    data object Tomorrow : DayLabel
    data object Yesterday : DayLabel

    /** ISO day number: 1 = Monday … 7 = Sunday. */
    data class Weekday(val isoDay: Int) : DayLabel

    data class Date(val day: Int, val month: Int, val year: Int?) : DayLabel

    companion object {
        fun of(date: LocalDate, today: LocalDate): DayLabel = when (val days = today.daysUntil(date)) {
            0 -> Today
            1 -> Tomorrow
            -1 -> Yesterday
            in 2..6 -> Weekday(date.dayOfWeek.isoDayNumber)
            else -> shortDate(date, today)
        }

        /** A short date always ("Fri, Nov 27"-style headers of Upcoming use the weekday separately). */
        fun shortDate(date: LocalDate, today: LocalDate): Date =
            Date(date.day, date.month.number, if (date.year != today.year) date.year else null)
    }
}

private val WEEKDAYS: List<StringResource> = listOf(
    Res.string.weekday_1, Res.string.weekday_2, Res.string.weekday_3, Res.string.weekday_4,
    Res.string.weekday_5, Res.string.weekday_6, Res.string.weekday_7,
)

private val MONTHS: List<StringResource> = listOf(
    Res.string.month_1, Res.string.month_2, Res.string.month_3, Res.string.month_4, Res.string.month_5, Res.string.month_6,
    Res.string.month_7, Res.string.month_8, Res.string.month_9, Res.string.month_10, Res.string.month_11, Res.string.month_12,
)

@Composable
fun weekdayName(isoDay: Int): String = stringResource(WEEKDAYS[isoDay - 1])

@Composable
fun DayLabel.text(): String = when (this) {
    DayLabel.Today -> stringResource(Res.string.date_today)
    DayLabel.Tomorrow -> stringResource(Res.string.date_tomorrow)
    DayLabel.Yesterday -> stringResource(Res.string.date_yesterday)
    is DayLabel.Weekday -> weekdayName(isoDay)
    is DayLabel.Date -> {
        val month = stringResource(MONTHS[month - 1])
        if (year == null) stringResource(Res.string.date_day_month, day, month)
        else stringResource(Res.string.date_day_month_year, day, month, year)
    }
}

/** "Tomorrow 14:00", "Fri", "27 лист. 09:00 (Warsaw)": date label, time, and the zone when it is not the device's. */
@Composable
fun dueText(due: Due, today: LocalDate, deviceZone: TimeZone = TimeZones.systemDefault()): String {
    val parts = mutableListOf(DayLabel.of(due.date, today).text())
    due.time?.let { parts += clock(it) }
    due.zoneId?.takeIf { it != deviceZone.id }?.let { parts += "(${zoneCity(it)})" }
    return parts.joinToString(" ")
}

/** 24-hour clock in every language for Ф1a; the system 12/24-hour setting is read in Ф6c. */
fun clock(time: LocalTime): String = "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

/** "Europe/Warsaw" → "Warsaw", "America/New_York" → "New York". */
fun zoneCity(zoneId: String): String = zoneId.substringAfterLast('/').replace('_', ' ')

/** Days from [today] to [deadline]: negative when it has passed. */
fun deadlineDaysLeft(deadline: LocalDate, today: LocalDate): Int = today.daysUntil(deadline)

/** "today", "3 days", "passed" (UX_SPEC §3.1: the deadline with what is left). */
@Composable
fun deadlineText(daysLeft: Int): String = when {
    daysLeft < 0 -> stringResource(Res.string.deadline_passed)
    daysLeft == 0 -> stringResource(Res.string.deadline_today)
    else -> pluralStringResource(Res.plurals.deadline_days, daysLeft, daysLeft)
}

/**
 * The device's date, re-emitted at every local midnight (UX_SPEC §3.1: Today and Upcoming move on
 * at midnight without a restart). [zone] is read again each day, so a time-zone change counts.
 */
class DayTicker(private val clock: Clock, private val zone: () -> TimeZone) {
    val today: Flow<LocalDate> = flow {
        while (true) {
            val now = clock.now()
            val tz = zone()
            val day = now.toLocalDateTime(tz).date
            emit(day)
            val nextMidnight = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz)
            // A second past midnight, and at least a second: clocks that jump never spin this loop.
            delay(maxOf(nextMidnight - now + 1.seconds, 1.seconds))
        }
    }.distinctUntilChanged()

    fun now(): LocalDate = clock.now().toLocalDateTime(zone()).date
}
