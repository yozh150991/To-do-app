package io.github.yozh150991.todo.core.domain

import io.github.yozh150991.todo.core.model.Due
import io.github.yozh150991.todo.core.model.Priority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Today and Upcoming queries of plan Ф1a PR 3 (UX_SPEC §3.1). */
class TaskQueriesTest {
    private val f = RepoFixture()
    private val today = LocalDate(2026, 11, 21)

    @AfterTest
    fun tearDown() = f.close()

    private suspend fun task(title: String, date: LocalDate? = null, time: LocalTime? = null, priority: Priority = Priority.P4, project: String? = null, deadline: LocalDate? = null) =
        f.repo.createTask(title, projectId = project, priority = priority, due = date?.let { Due(it, time) }, deadline = deadline)

    @Test
    fun todayHasOverdueTodayAndTodaysDeadlinesInOrder() = runBlocking<Unit> {
        task("overdue", LocalDate(2026, 11, 19))
        task("all day p1", today, priority = Priority.P1)
        task("all day p4", today)
        task("at 18:00", today, LocalTime(18, 0))
        task("at 09:00", today, LocalTime(9, 0))
        task("tomorrow", LocalDate(2026, 11, 22))
        task("no date")
        task("deadline today, no date", deadline = today)
        task("deadline tomorrow, no date", deadline = LocalDate(2026, 11, 22))
        val done = task("done today", today)
        f.repo.complete(done.id)
        val binned = task("binned today", today)
        f.repo.delete(binned.id)
        val archived = f.repo.projects.create("Archived")
        task("in an archived project", today, project = archived.id)
        f.repo.projects.archive(archived.id)

        val titles = f.repo.observeToday(today).first().map { it.content }
        assertEquals(
            listOf("deadline today, no date", "overdue", "at 09:00", "at 18:00", "all day p1", "all day p4"),
            titles,
            "no date sorts first in SQL; the screen puts it in its own group",
        )
    }

    @Test
    fun upcomingIsTheDateRangeInclusive() = runBlocking<Unit> {
        task("today", today)
        task("first day", LocalDate(2026, 11, 22))
        task("new year", LocalDate(2027, 1, 1), LocalTime(0, 30))
        task("last day", LocalDate(2027, 1, 20))
        task("after", LocalDate(2027, 1, 21))
        val titles = f.repo.observeDueBetween(LocalDate(2026, 11, 22), LocalDate(2027, 1, 20)).first().map { it.content }
        assertEquals(listOf("first day", "new year", "last day"), titles)
    }
}
