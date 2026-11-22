package io.github.yozh150991.todo.ui

import io.github.yozh150991.todo.core.model.Due
import io.github.yozh150991.todo.core.model.Task
import io.github.yozh150991.todo.ui.tasklist.DayLabel
import io.github.yozh150991.todo.ui.tasklist.DayTicker
import io.github.yozh150991.todo.ui.tasklist.deadlineDaysLeft
import io.github.yozh150991.todo.ui.tasklist.treeOrder
import io.github.yozh150991.todo.ui.tasklist.zoneCity
import io.github.yozh150991.todo.ui.today.TodayGroups
import io.github.yozh150991.todo.ui.upcoming.UpcomingViewModel
import io.github.yozh150991.todo.core.model.TimeZones
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** The pure parts of the Ф1a lists (plan PR 3): dates, groups, days, tree order, midnight. */
class ListsTest {
    private val today = LocalDate(2026, 11, 21) // a Saturday

    private fun task(id: String, date: LocalDate? = null, parent: String? = null, order: String = id, deadline: LocalDate? = null) = Task(
        id = id,
        projectId = "p",
        parentId = parent,
        childOrder = order,
        content = id,
        due = date?.let { Due(it) },
        deadline = deadline,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
    )

    /** UX_SPEC §8: Today, Tomorrow, Yesterday, a weekday within the week, then a date; the year only when it differs. */
    @Test
    fun dayLabels() {
        assertEquals(DayLabel.Today, DayLabel.of(today, today))
        assertEquals(DayLabel.Tomorrow, DayLabel.of(LocalDate(2026, 11, 22), today))
        assertEquals(DayLabel.Yesterday, DayLabel.of(LocalDate(2026, 11, 20), today))
        assertEquals(DayLabel.Weekday(1), DayLabel.of(LocalDate(2026, 11, 23), today), "Monday")
        assertEquals(DayLabel.Weekday(5), DayLabel.of(LocalDate(2026, 11, 27), today), "Friday, six days ahead")
        assertEquals(DayLabel.Date(28, 11, null), DayLabel.of(LocalDate(2026, 11, 28), today), "a week ahead: the date")
        assertEquals(DayLabel.Date(10, 11, null), DayLabel.of(LocalDate(2026, 11, 10), today))
        assertEquals(DayLabel.Date(3, 1, 2027), DayLabel.of(LocalDate(2027, 1, 3), today), "next year shows the year")
        assertEquals(DayLabel.Date(31, 12, 2025), DayLabel.of(LocalDate(2025, 12, 31), today))
    }

    @Test
    fun deadlinesAndZones() {
        assertEquals(0, deadlineDaysLeft(today, today))
        assertEquals(3, deadlineDaysLeft(LocalDate(2026, 11, 24), today))
        assertEquals(-2, deadlineDaysLeft(LocalDate(2026, 11, 19), today))
        assertEquals(42, deadlineDaysLeft(LocalDate(2027, 1, 2), today), "across the year")
        assertEquals("Warsaw", zoneCity("Europe/Warsaw"))
        assertEquals("New York", zoneCity("America/New_York"))
    }

    @Test
    fun todayGroupsSplitOverdueTodayAndDeadlines() {
        val groups = TodayGroups.of(
            listOf(
                task("no date, deadline today", deadline = today),
                task("no date, deadline tomorrow", deadline = LocalDate(2026, 11, 22)),
                task("overdue", LocalDate(2026, 11, 1)),
                task("today", today),
            ),
            today,
        )
        assertEquals(listOf("overdue"), groups.overdue.map { it.id })
        assertEquals(listOf("today"), groups.today.map { it.id })
        assertEquals(listOf("no date, deadline today"), groups.deadlineToday.map { it.id })
    }

    /** Owner's decision: every day, empty ones too, across month and year ends. */
    @Test
    fun upcomingHasEveryDayAcrossMonthAndYearEnds() {
        val first = LocalDate(2026, 12, 30)
        val days = UpcomingViewModel.daysOf(first, 4, listOf(task("a", LocalDate(2026, 12, 31)), task("b", LocalDate(2027, 1, 2))))
        assertEquals(listOf(LocalDate(2026, 12, 30), LocalDate(2026, 12, 31), LocalDate(2027, 1, 1), LocalDate(2027, 1, 2)), days.map { it.date })
        assertEquals(listOf(0, 1, 0, 1), days.map { it.tasks.size })
    }

    @Test
    fun treeOrderPutsSubtasksUnderTheirParents() {
        val items = treeOrder(
            listOf(
                task("b", order = "b"),
                task("a1", parent = "a", order = "a"),
                task("a", order = "a"),
                task("a2", parent = "a", order = "b"),
                task("a1x", parent = "a1", order = "a"),
                task("orphan", parent = "missing", order = "c"),
            ),
        )
        assertEquals(listOf("a" to 0, "a1" to 1, "a1x" to 2, "a2" to 1, "b" to 0, "orphan" to 0), items.map { it.task.id to it.depth })
    }

    /** UX_SPEC §3.1: Today and Upcoming move on at local midnight without a restart. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun dayTickerMovesOnAtMidnight() = runTest {
        val zone = TimeZones.of("Europe/Warsaw")
        val start = LocalDateTime(2026, 11, 21, 23, 59, 0).toInstant(zone)
        // A clock on the virtual time of the test scheduler.
        val clock = object : Clock {
            override fun now(): Instant = start + testScheduler.currentTime.milliseconds
        }
        val days = DayTicker(clock) { zone }.today.take(3).toList()
        assertEquals(listOf(LocalDate(2026, 11, 21), LocalDate(2026, 11, 22), LocalDate(2026, 11, 23)), days)
        // 61 s to the first midnight (plus a second), then exactly a day to the next.
        assertEquals(61_000L + 86_400_000L, testScheduler.currentTime)
    }
}
