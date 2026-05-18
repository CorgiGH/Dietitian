package com.dietician.shared.ui.data

import kotlin.math.abs

/**
 * MacroFactor-style adaptive expenditure (TDEE) estimator.
 *
 * Simple Bayesian rolling estimate combining a Mifflin-St-Jeor prior with EWMA
 * smoothing against actual intake + weight-delta evidence:
 *
 *   TDEE_t = TDEE_{t-1} + alpha * (intake_t - delta_weight_t * 7700 - TDEE_{t-1})
 *
 * where:
 *   - alpha = 0.2 (EWMA factor)
 *   - 7700 kcal ≈ 1 kg body-mass (mixed lean/fat energy density approximation)
 *
 * Documented as an APPROXIMATION, not medical advice. Surface guarded by
 * Art 4 AI Literacy banner — already shown at first-launch.
 *
 * Returns the rolling 7-day estimate series; UI charts the last data point as
 * "today's estimate" and computes the 7d adjustment vs the prior.
 */
object ExpenditureEstimator {

    /** EWMA smoothing factor. Heavier weight on prior; recent evidence nudges. */
    const val ALPHA: Double = 0.2

    /** kcal ≈ 1 kg body-mass conversion. */
    const val KCAL_PER_KG: Double = 7700.0

    /**
     * Compute the Mifflin-St-Jeor BMR prior, multiplied by an activity factor.
     *
     * @param kg body weight in kilograms
     * @param cm height in centimetres
     * @param age years
     * @param activityFactor multiplier (1.2 sedentary, 1.55 moderate, 1.725 active)
     * @param male sex assigned at birth-equivalent for BMR formula
     */
    fun mifflinStJeor(
        kg: Double,
        cm: Double,
        age: Int,
        activityFactor: Double = 1.55,
        male: Boolean = true,
    ): Double {
        val baseBmr = 10.0 * kg + 6.25 * cm - 5.0 * age + (if (male) 5.0 else -161.0)
        return baseBmr * activityFactor
    }

    /**
     * Posterior series. Input is the last N days of [weight_kg, intake_kcal];
     * series[0] is the oldest day. Returns the per-day TDEE estimate (same length).
     *
     * @param prior baseline TDEE estimate (e.g. from [mifflinStJeor])
     * @param days  list of (weight, intake) pairs ordered oldest-first
     */
    fun rollingTdee(prior: Double, days: List<DayPoint>): List<Double> {
        if (days.isEmpty()) return emptyList()
        val result = ArrayList<Double>(days.size)
        var tdeePrev = prior
        for (i in days.indices) {
            val day = days[i]
            val deltaWeight = if (i == 0) 0.0 else day.weightKg - days[i - 1].weightKg
            // intake - deltaWeight*7700 = inferred actual expenditure for this day window
            val observed = day.intakeKcal - deltaWeight * KCAL_PER_KG
            val updated = tdeePrev + ALPHA * (observed - tdeePrev)
            result += updated
            tdeePrev = updated
        }
        return result
    }

    /**
     * Today's TDEE estimate + 7-day adjustment from the prior.
     * Returns null if the input series is empty.
     */
    fun summarize(prior: Double, days: List<DayPoint>): ExpenditureSummary? {
        val series = rollingTdee(prior, days)
        if (series.isEmpty()) return null
        val todayEstimate = series.last()
        val adjustment = todayEstimate - prior
        return ExpenditureSummary(
            todayEstimate = todayEstimate,
            adjustmentVsPrior = adjustment,
            series = series,
        )
    }

    /**
     * Stable rounding for chart annotations. Avoids JVM-only String.format.
     */
    fun roundKcal(v: Double): Int = (v + if (v >= 0) 0.5 else -0.5).toInt()

    /** True if the absolute adjustment is below 50 kcal — chart shows "stable". */
    fun isStable(summary: ExpenditureSummary): Boolean =
        abs(summary.adjustmentVsPrior) < 50.0
}

/** Day-level input — weight (kg, morning fasted) + intake (kcal, full day). */
data class DayPoint(val weightKg: Double, val intakeKcal: Double)

data class ExpenditureSummary(
    val todayEstimate: Double,
    val adjustmentVsPrior: Double,
    val series: List<Double>,
)
