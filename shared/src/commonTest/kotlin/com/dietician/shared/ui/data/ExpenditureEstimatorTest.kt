package com.dietician.shared.ui.data

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExpenditureEstimatorTest {

    @Test
    fun `Mifflin-St-Jeor for 80kg 180cm 25y male active produces sensible BMR x activity`() {
        val tdee = ExpenditureEstimator.mifflinStJeor(
            kg = 80.0,
            cm = 180.0,
            age = 25,
            activityFactor = 1.55,
            male = true,
        )
        // BMR = 10*80 + 6.25*180 - 5*25 + 5 = 1805; *1.55 ≈ 2798
        assertTrue(tdee in 2700.0..2900.0, "got tdee=$tdee")
    }

    @Test
    fun `Mifflin-St-Jeor female 60kg 165cm 30y produces lower BMR`() {
        val tdee = ExpenditureEstimator.mifflinStJeor(
            kg = 60.0,
            cm = 165.0,
            age = 30,
            activityFactor = 1.2,
            male = false,
        )
        // BMR = 10*60 + 6.25*165 - 5*30 - 161 = 1320.25; *1.2 = 1584
        assertTrue(tdee in 1500.0..1700.0, "got tdee=$tdee")
    }

    @Test
    fun `rollingTdee empty input returns empty`() {
        assertTrue(ExpenditureEstimator.rollingTdee(2500.0, emptyList()).isEmpty())
    }

    @Test
    fun `rollingTdee returns one estimate per input day`() {
        val days = listOf(
            DayPoint(80.0, 2500.0),
            DayPoint(80.0, 2500.0),
            DayPoint(80.0, 2500.0),
        )
        val series = ExpenditureEstimator.rollingTdee(2500.0, days)
        assertEquals(3, series.size)
    }

    @Test
    fun `stable weight + intake at prior keeps TDEE at prior`() {
        val days = (1..7).map { DayPoint(80.0, 2500.0) }
        val series = ExpenditureEstimator.rollingTdee(2500.0, days)
        // With deltaW = 0 + intake = prior, EWMA update is no-op every day.
        for (v in series) {
            assertTrue(abs(v - 2500.0) < 1.0, "expected ~2500, got $v")
        }
    }

    @Test
    fun `weight loss with same intake lowers TDEE estimate via EWMA`() {
        // 7 days; lose 0.2kg/day at intake = 2200 → inferred expenditure higher
        // than reported intake → TDEE estimate climbs above prior intake.
        val days = (0..6).map { DayPoint(80.0 - it * 0.2, 2200.0) }
        val series = ExpenditureEstimator.rollingTdee(2200.0, days)
        // After 7d, TDEE estimate > intake because observed = intake + delta*7700 > intake
        assertTrue(series.last() > 2200.0, "got tail=${series.last()}")
    }

    @Test
    fun `weight gain with same intake raises observed → lowers TDEE estimate`() {
        // Gain 0.1kg/day at intake = 3000 → inferred expenditure lower than intake.
        val days = (0..6).map { DayPoint(80.0 + it * 0.1, 3000.0) }
        val series = ExpenditureEstimator.rollingTdee(3000.0, days)
        assertTrue(series.last() < 3000.0, "got tail=${series.last()}")
    }

    @Test
    fun `summarize returns null for empty series`() {
        assertNull(ExpenditureEstimator.summarize(2500.0, emptyList()))
    }

    @Test
    fun `summarize today estimate equals last series entry`() {
        val days = listOf(DayPoint(80.0, 2400.0), DayPoint(79.9, 2400.0))
        val summary = ExpenditureEstimator.summarize(2500.0, days)
        assertNotNull(summary)
        assertEquals(summary.series.last(), summary.todayEstimate)
    }

    @Test
    fun `roundKcal positive rounds half up`() {
        assertEquals(2500, ExpenditureEstimator.roundKcal(2499.6))
        assertEquals(2500, ExpenditureEstimator.roundKcal(2500.4))
    }

    @Test
    fun `roundKcal negative rounds half away from zero`() {
        assertEquals(-15, ExpenditureEstimator.roundKcal(-15.4))
        assertEquals(-16, ExpenditureEstimator.roundKcal(-15.6))
    }

    @Test
    fun `isStable true when adjustment magnitude lt 50 kcal`() {
        val summary = ExpenditureSummary(
            todayEstimate = 2530.0,
            adjustmentVsPrior = 30.0,
            series = listOf(2530.0),
        )
        assertTrue(ExpenditureEstimator.isStable(summary))
    }

    @Test
    fun `isStable false when adjustment magnitude gte 50 kcal`() {
        val summary = ExpenditureSummary(
            todayEstimate = 2580.0,
            adjustmentVsPrior = 80.0,
            series = listOf(2580.0),
        )
        assertTrue(!ExpenditureEstimator.isStable(summary))
    }
}
