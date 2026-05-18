package com.dietician.shared.ui.data

import com.dietician.shared.data.api.EventPayload
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeightAggregatorTest {

    private fun mkWeight(date: String, kg: Double): EventPayload.Weight {
        val parts = date.split("-").map { it.toInt() }
        val ldt = LocalDateTime(parts[0], parts[1], parts[2], 12, 0)
        return EventPayload.Weight(
            eventUuid = "u-$date-$kg",
            deviceId = "victor-desktop",
            originatedAtMs = ldt.toInstant(TimeZone.UTC).toEpochMilliseconds(),
            weightKg = kg,
        )
    }

    @Test
    fun `empty input → empty output`() {
        assertTrue(WeightAggregator.aggregate(emptyList()).isEmpty())
    }

    @Test
    fun `single event → single week with n=1`() {
        val out = WeightAggregator.aggregate(listOf(mkWeight("2026-05-18", 80.5)))
        assertEquals(1, out.size)
        assertEquals(80.5, out.first().meanKg, 0.0001)
        assertEquals(1, out.first().n)
    }

    @Test
    fun `multiple events in same ISO week aggregate to one bucket`() {
        // 2026-05-18 is Monday; 2026-05-19 Tue; 2026-05-20 Wed.
        val events = listOf(
            mkWeight("2026-05-18", 80.0),
            mkWeight("2026-05-19", 80.4),
            mkWeight("2026-05-20", 80.8),
        )
        val out = WeightAggregator.aggregate(events)
        assertEquals(1, out.size)
        assertEquals(80.4, out.first().meanKg, 0.0001)
        assertEquals(3, out.first().n)
    }

    @Test
    fun `events spanning two weeks produce two buckets ordered ascending`() {
        // 2026-05-18 (Mon) week W1; 2026-05-25 (Mon next week) W2.
        val events = listOf(
            mkWeight("2026-05-25", 81.0),
            mkWeight("2026-05-18", 80.0),
        )
        val out = WeightAggregator.aggregate(events)
        assertEquals(2, out.size)
        assertTrue(out[0].weekStart < out[1].weekStart)
        assertEquals(80.0, out[0].meanKg, 0.0001)
        assertEquals(81.0, out[1].meanKg, 0.0001)
    }

    @Test
    fun `isoWeekStart of Monday returns same Monday`() {
        val mon = LocalDate(2026, 5, 18) // Monday
        assertEquals(mon, WeightAggregator.isoWeekStart(mon))
    }

    @Test
    fun `isoWeekStart of Sunday rolls back 6 days to Monday`() {
        val sun = LocalDate(2026, 5, 24) // Sunday
        val mon = LocalDate(2026, 5, 18) // Monday
        assertEquals(mon, WeightAggregator.isoWeekStart(sun))
    }

    @Test
    fun `trendKgPerWeek on 1 point returns 0`() {
        val series = listOf(WeeklyWeight(LocalDate(2026, 5, 18), 80.0, 1))
        assertEquals(0.0, WeightAggregator.trendKgPerWeek(series), 0.0001)
    }

    @Test
    fun `trendKgPerWeek on flat series returns ~0`() {
        val series = (0..3).map {
            WeeklyWeight(LocalDate(2026, 5, 18).plusWeeks(it), 80.0, 7)
        }
        assertTrue(abs(WeightAggregator.trendKgPerWeek(series)) < 0.0001)
    }

    @Test
    fun `trendKgPerWeek on rising series returns positive slope`() {
        val series = (0..3).map {
            WeeklyWeight(LocalDate(2026, 5, 18).plusWeeks(it), 80.0 + it * 0.4, 7)
        }
        val slope = WeightAggregator.trendKgPerWeek(series)
        assertTrue(slope > 0.39 && slope < 0.41, "got slope=$slope")
    }

    @Test
    fun `trendKgPerWeek on falling series returns negative slope`() {
        val series = (0..3).map {
            WeeklyWeight(LocalDate(2026, 5, 18).plusWeeks(it), 80.0 - it * 0.3, 7)
        }
        val slope = WeightAggregator.trendKgPerWeek(series)
        assertTrue(slope < -0.29 && slope > -0.31, "got slope=$slope")
    }

    @Test
    fun `lastNWeeks returns trailing window`() {
        val series = (0..9).map {
            WeeklyWeight(LocalDate(2026, 1, 5).plusWeeks(it), 80.0 + it, 1)
        }
        val tail = WeightAggregator.lastNWeeks(series, 4)
        assertEquals(4, tail.size)
        assertEquals(86.0, tail.first().meanKg, 0.0001)
        assertEquals(89.0, tail.last().meanKg, 0.0001)
    }

    @Test
    fun `lastNWeeks shorter than window returns whole series`() {
        val series = (0..2).map {
            WeeklyWeight(LocalDate(2026, 5, 18).plusWeeks(it), 80.0 + it, 1)
        }
        val tail = WeightAggregator.lastNWeeks(series, 12)
        assertEquals(3, tail.size)
    }
}

private fun LocalDate.plusWeeks(weeks: Int): LocalDate =
    LocalDate.fromEpochDays(this.toEpochDays() + weeks * 7)
