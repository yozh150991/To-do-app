package io.github.yozh150991.todo.ui.tasklist

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.yozh150991.todo.core.model.Task
import io.github.yozh150991.todo.ui.resources.Res
import io.github.yozh150991.todo.ui.resources.cancel
import io.github.yozh150991.todo.ui.resources.ic_check
import io.github.yozh150991.todo.ui.resources.ic_clear
import io.github.yozh150991.todo.ui.resources.ic_schedule
import io.github.yozh150991.todo.ui.resources.ok
import io.github.yozh150991.todo.ui.resources.schedule_next_week
import io.github.yozh150991.todo.ui.resources.schedule_no_date
import io.github.yozh150991.todo.ui.resources.schedule_pick
import io.github.yozh150991.todo.ui.resources.schedule_title
import io.github.yozh150991.todo.ui.resources.schedule_today
import io.github.yozh150991.todo.ui.resources.schedule_tomorrow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.time.Instant

/**
 * How far a row must travel, as a share of its width, for a swipe to count (UX_SPEC §4): a
 * quarter — about 100 dp on a phone, a deliberate move, while a short drag springs back.
 */
internal const val SWIPE_THRESHOLD = 0.25f

private val CompleteColor = Color(0xFF2E7D32)
private val ScheduleColor = Color(0xFF6A4C93)

/**
 * A row that can be swiped (UX_SPEC §4): right — [onSwipeRight] (complete), left — [onSwipeLeft]
 * (schedule). Short swipes spring back. The swipe starts on the row only, never at the screen edge
 * where the system back gesture lives; TalkBack users get the same actions on the row itself.
 */
@Composable
fun SwipeBox(
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    var offset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }
    val drag = rememberDraggableState { delta -> offset = (offset + delta).coerceIn(-width.toFloat(), width.toFloat()) }
    Box(modifier.onSizeChanged { width = it.width }) {
        if (offset != 0f) {
            val right = offset > 0f
            Box(
                Modifier
                    .matchParentSize()
                    .background(if (right) CompleteColor else ScheduleColor)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (right) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(
                    painterResource(if (right) Res.drawable.ic_check else Res.drawable.ic_schedule),
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.roundToInt(), 0) }
                .draggable(
                    state = drag,
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    onDragStopped = {
                        val threshold = width * SWIPE_THRESHOLD
                        when {
                            threshold > 0f && offset >= threshold -> {
                                animate(offset, width.toFloat()) { value, _ -> offset = value }
                                onSwipeRight()
                                offset = 0f
                            }
                            threshold > 0f && offset <= -threshold -> {
                                onSwipeLeft()
                                animate(offset, 0f) { value, _ -> offset = value }
                            }
                            else -> animate(offset, 0f) { value, _ -> offset = value }
                        }
                    },
                ),
        ) { content() }
    }
}

/** A task row of a list with the swipes of UX_SPEC §4; completed rows do not swipe. */
@Composable
fun SwipeableTaskRow(
    item: TaskItem,
    today: LocalDate,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onSchedule: () -> Unit,
    modifier: Modifier = Modifier,
    projectName: String? = null,
    selected: Boolean = false,
) {
    SwipeBox(onSwipeRight = onToggle, onSwipeLeft = onSchedule, modifier = modifier, enabled = !item.task.isCompleted) {
        TaskRow(item, today, onOpen, onToggle, projectName = projectName, selected = selected, onSchedule = onSchedule)
    }
}

/** "Next week" is the next Monday (the week starts on Monday, UX_SPEC §3.5). */
fun nextWeek(today: LocalDate): LocalDate = today.plus(8 - today.dayOfWeek.isoDayNumber, DateTimeUnit.DAY)

/**
 * The date choices of a swipe left (UX_SPEC §4): Today, Tomorrow, Next week, No date (when the
 * task has one) and a calendar. [onPick] gets the date, or null for "No date".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSheet(task: Task, today: LocalDate, onPick: (LocalDate?) -> Unit, onDismiss: () -> Unit) {
    var calendar by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, modifier = Modifier.testTag("schedule-sheet")) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                stringResource(Res.string.schedule_title, task.content),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
            )
            fun choose(date: LocalDate?) {
                onPick(date)
                onDismiss()
            }
            ScheduleChoice(stringResource(Res.string.schedule_today), weekdayName(today.dayOfWeek.isoDayNumber), "schedule-today") { choose(today) }
            val tomorrow = today.plus(1, DateTimeUnit.DAY)
            ScheduleChoice(stringResource(Res.string.schedule_tomorrow), weekdayName(tomorrow.dayOfWeek.isoDayNumber), "schedule-tomorrow") { choose(tomorrow) }
            val monday = nextWeek(today)
            ScheduleChoice(
                stringResource(Res.string.schedule_next_week),
                "${weekdayName(monday.dayOfWeek.isoDayNumber)} ${DayLabel.shortDate(monday, today).text()}",
                "schedule-next-week",
            ) { choose(monday) }
            if (task.due != null) ScheduleChoice(stringResource(Res.string.schedule_no_date), null, "schedule-none") { choose(null) }
            ScheduleChoice(stringResource(Res.string.schedule_pick), null, "schedule-pick") { calendar = true }
        }
    }
    if (calendar) {
        DateDialog(
            initial = task.due?.date ?: today,
            onPick = { date ->
                calendar = false
                onPick(date)
                onDismiss()
            },
            onDismiss = { calendar = false },
        )
    }
}

@Composable
private fun ScheduleChoice(label: String, hint: String?, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(horizontal = 24.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        if (hint != null) Text(hint, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The date picker works in UTC midnights; a calendar date never passes through the device zone. */
internal fun LocalDate.pickerMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

internal fun pickerDate(millis: Long): LocalDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date

/** A calendar for one date (Material date picker). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.pickerMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPick(pickerDate(it)) } ?: onDismiss() },
                modifier = Modifier.testTag("date-ok"),
            ) { Text(stringResource(Res.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

/** A clock for the due time, 24-hour in Ф1a (the system format is read in Ф6c). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime(state.hour, state.minute)) }, modifier = Modifier.testTag("time-ok")) {
                Text(stringResource(Res.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        text = { TimePicker(state = state) },
    )
}

/** A small "×" that clears a field. */
@Composable
fun ClearIcon(description: String) {
    Icon(painterResource(Res.drawable.ic_clear), contentDescription = description, modifier = Modifier.padding(4.dp))
}
