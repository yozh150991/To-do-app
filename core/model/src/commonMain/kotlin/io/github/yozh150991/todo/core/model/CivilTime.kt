package io.github.yozh150991.todo.core.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/*
 * Civil date/time → instant (spec §4.0.1 п. 4 and п. 7; golden fixtures TIME-01..TIME-04, TIME-08).
 *
 * Tasks store civil fields (date, time, optional IANA zone) and never a precomputed instant. Every
 * caller turns them into an instant through this one pure resolver and always passes the zone in:
 * nothing here reads the system clock or the system time zone.
 */

/** Where a civil time comes from; it decides what happens in a DST gap (spec §4.0.1 п. 4). */
enum class CivilTimeOrigin {
    /** Produced by a recurrence rule: a time in a DST gap moves forward by the gap and still counts. */
    GENERATED,

    /** Typed by the user for one date: a time in a DST gap is a validation error with choices. */
    EXPLICIT,
}

sealed interface CivilResolution {
    val requested: LocalDateTime
    val zone: TimeZone

    /** The local time exists exactly once in the zone. */
    data class Exact(
        override val requested: LocalDateTime,
        override val zone: TimeZone,
        val instant: Instant,
    ) : CivilResolution

    /**
     * DST gap, generated occurrence: shifted forward by the gap length (02:30 → 03:30), i.e. the
     * instant that 02:30 has at the offset before the gap. It still counts towards COUNT.
     */
    data class ShiftedForward(
        override val requested: LocalDateTime,
        override val zone: TimeZone,
        val instant: Instant,
        val actual: LocalDateTime,
        val gap: Duration,
    ) : CivilResolution

    /** DST overlap: the local time happens twice; the earlier instant is used, never two occurrences. */
    data class EarlierOfTwo(
        override val requested: LocalDateTime,
        override val zone: TimeZone,
        val instant: Instant,
        val laterInstant: Instant,
    ) : CivilResolution

    /**
     * DST gap, explicit one-shot time: no silent shift and no alarm. [choices] are valid local times
     * to offer instead: the first minute after the gap and the time shifted by the gap length.
     */
    data class Nonexistent(
        override val requested: LocalDateTime,
        override val zone: TimeZone,
        val gap: Duration,
        val choices: List<LocalDateTime>,
    ) : CivilResolution

    /** The instant to schedule, or null when the user has to choose another time first. */
    val instantOrNull: Instant?
        get() = when (this) {
            is Exact -> instant
            is ShiftedForward -> instant
            is EarlierOfTwo -> instant
            is Nonexistent -> null
        }
}

object CivilTime {
    /** Offset transitions closer than this to each other are not supported (none exist in tzdb). */
    private val SEARCH_WINDOW = 26.hours

    fun resolve(date: LocalDate, time: LocalTime, zone: TimeZone, origin: CivilTimeOrigin): CivilResolution =
        resolve(LocalDateTime(date, time), zone, origin)

    fun resolve(requested: LocalDateTime, zone: TimeZone, origin: CivilTimeOrigin): CivilResolution {
        // Every UTC offset in tzdb lies within ±18 h, so the offsets in effect a full search window
        // before and after "requested read as UTC" are the offsets on both sides of any nearby transition.
        val asUtc = requested.toInstant(UtcOffset.ZERO)
        val before = zone.offsetAt(asUtc - SEARCH_WINDOW)
        val after = zone.offsetAt(asUtc + SEARCH_WINDOW)
        val valid = listOf(before, after).distinct()
            .map { offset -> requested.toInstant(offset) }
            .filter { instant -> instant.toLocalDateTime(zone) == requested }
            .distinct()
            .sorted()

        return when {
            valid.size == 1 -> CivilResolution.Exact(requested, zone, valid.single())
            valid.size >= 2 -> CivilResolution.EarlierOfTwo(requested, zone, valid.first(), valid.last())
            else -> {
                val gap = (after.totalSeconds - before.totalSeconds).seconds
                val shifted = requested.toInstant(before)
                val actual = shifted.toLocalDateTime(zone)
                when (origin) {
                    CivilTimeOrigin.GENERATED -> CivilResolution.ShiftedForward(requested, zone, shifted, actual, gap)
                    CivilTimeOrigin.EXPLICIT -> {
                        val firstAfterGap = transitionInstant(zone, requested.toInstant(after), shifted, after)
                            .toLocalDateTime(zone)
                        CivilResolution.Nonexistent(requested, zone, gap, listOf(firstAfterGap, actual).distinct())
                    }
                }
            }
        }
    }

    /**
     * Where the gap ends: the transition instant between [from] (old offset) and [to] ([newOffset]).
     * Bisection down to a minute, then the whole minute inside that window (tzdb transitions fall
     * on whole minutes).
     */
    private fun transitionInstant(zone: TimeZone, from: Instant, to: Instant, newOffset: UtcOffset): Instant {
        var low = from
        var high = to
        while (high - low > 1.minutes) {
            val middle = low + (high - low) / 2
            if (zone.offsetAt(middle) == newOffset) high = middle else low = middle
        }
        val nextMinute = Instant.fromEpochSeconds(low.epochSeconds.floorDiv(60L) * 60L + 60L)
        return if (nextMinute <= high && zone.offsetAt(nextMinute) == newOffset) nextMinute else high
    }
}

/**
 * The zone a due time is read in (spec §4: `fixed_zone` keeps its IANA zone, floating local time
 * follows the device). TIME-03: a fixed zone ignores where the device is; TIME-04: floating does not.
 */
fun Due.zone(deviceZone: TimeZone): TimeZone = zoneId?.let(TimeZones::of) ?: deviceZone

/** Instant resolution of a timed due; null for an all-day date, which is never converted to an instant. */
fun Due.resolve(deviceZone: TimeZone, origin: CivilTimeOrigin): CivilResolution? =
    time?.let { CivilTime.resolve(date, it, zone(deviceZone), origin) }

/** IANA zones by id. On the Web the zone rules come from @js-joda/timezone, loaded on first use. */
object TimeZones {
    fun of(id: String): TimeZone {
        ensureTimeZoneRules()
        return TimeZone.of(id)
    }
}

/** Makes the platform's IANA rules available to kotlinx-datetime (only the Web needs to do anything). */
internal expect fun ensureTimeZoneRules()
