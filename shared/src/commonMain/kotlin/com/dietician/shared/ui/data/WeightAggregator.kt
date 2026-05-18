package com.dietician.shared.ui.data

import com.dietician.shared.data.api.EventPayload
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Aggregates raw weight_events from the Plan-1 ledger into weekly mean-kg
 * buckets for the WeightTrendChart.
 *
 * Per spec §6.5 + RC5 bigorexia framing: the chart shows WEEKLY aggregate
 * (not daily weight) — Carbon-style trend rendering. Counter-bigorexia
 * primary surface: trend ONLY, not single-day measurement.
 *
 * Algorithm:
 *   1. Map each [EventPayload.Weight] to (ISO week-start LocalDate, kg)
 *   2. Bucket by week-start → mean kg + count
 *   3. Skip weeks with n=0 entries (no measurements that week → no point)
 *   4. Sort ascending by week-start
 *
 * Time zone: caller passes [zone]. UTC is the sane default for now; subject
 * timezone-aware aggregation lands in Plan-7.
 */
object WeightAggregator {

    /**
     * @param events raw weight_events from EventStore — any order; we sort
     * @param zone   timezone for the week-bucketing
     * @return list ordered oldest→newest with one [WeeklyWeight] per non-empty week
     */
    fun aggregate(
        events: List<EventPayload.Weight>,
        zone: TimeZone = TimeZone.UTC,
    ): List<WeeklyWeight> {
        if (events.isEmpty()) return emptyList()
        val grouped = HashMap<LocalDate, MutableList<Double>>()
        for (ev in events) {
            val date = Instant.fromEpochMilliseconds(ev.originatedAtMs)
                .toLocalDateTime(zone)
                .date
            val weekStart = isoWeekStart(date)
            grouped.getOrPut(weekStart) { mutableListOf() } += ev.weightKg
        }
        return grouped.entries
            .map { (week, kgs) ->
                WeeklyWeight(
                    weekStart = week,
                    meanKg = kgs.average(),
                    n = kgs.size,
                )
            }
            .filter { it.n > 0 }
            .sortedBy { it.weekStart }
    }

    /**
     * Compute the ISO Monday of the week containing [date].
     *
     * `java.time.DayOfWeek` (re-exported via kotlinx.datetime on JVM) is 1..7
     * with Mon=1. We use the `.ordinal + 1` form to stay multi-platform.
     */
    fun isoWeekStart(date: LocalDate): LocalDate {
        val dow = date.dayOfWeek.ordinal + 1 // Mon=1 .. Sun=7
        return date.minusDays(dow - 1)
    }

    /**
     * Simple linear regression slope (kg per week) over the [series] of points.
     * x = week-index (0..n-1), y = meanKg. Returns 0.0 if n < 2.
     */
    fun trendKgPerWeek(series: List<WeeklyWeight>): Double {
        if (series.size < 2) return 0.0
        val n = series.size
        val sumX = (0 until n).sum().toDouble()
        val sumY = series.sumOf { it.meanKg }
        val sumXY = series.indices.sumOf { i -> i.toDouble() * series[i].meanKg }
        val sumX2 = series.indices.sumOf { i -> i.toDouble() * i.toDouble() }
        val denom = n * sumX2 - sumX * sumX
        if (denom == 0.0) return 0.0
        return (n * sumXY - sumX * sumY) / denom
    }

    /**
     * Slice the trailing [windowWeeks] entries of a series. Less than the window
     * → return what we have.
     */
    fun lastNWeeks(series: List<WeeklyWeight>, windowWeeks: Int): List<WeeklyWeight> =
        series.takeLast(windowWeeks)
}

/** Aggregated weekly weight bucket. */
data class WeeklyWeight(
    val weekStart: LocalDate,
    val meanKg: Double,
    val n: Int,
)

/**
 * Helper extension — kotlinx.datetime [LocalDate.minus] requires the
 * DateTimeUnit overload in older versions; we use this convenience.
 */
private fun LocalDate.minusDays(days: Int): LocalDate {
    var d = this
    repeat(days) { d = d.minusOneDay() }
    return d
}

private fun LocalDate.minusOneDay(): LocalDate {
    // kotlinx.datetime LocalDate provides .minus(1, DateTimeUnit.DAY) but the
    // import surface varies. Use the safe path: toEpochDays() - 1 + back.
    val epochDay = this.toEpochDays() - 1
    return LocalDate.fromEpochDays(epochDay)
}
