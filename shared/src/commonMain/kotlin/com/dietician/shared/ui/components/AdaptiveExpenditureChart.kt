package com.dietician.shared.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.data.ExpenditureEstimator
import com.dietician.shared.ui.data.ExpenditureSummary
import com.dietician.shared.ui.i18n.strings
import com.dietician.shared.ui.theme.NeutralChip
import kotlin.math.max
import kotlin.math.min

/**
 * MacroFactor-style adaptive expenditure chart.
 *
 * Compose Canvas line chart:
 *   - x = day index (0 .. series.size - 1)
 *   - y = TDEE estimate kcal/day
 *
 * Overlay: horizontal target-intake line (dashed). NEUTRAL color tokens only
 * (no red/green axis per spec §6 + Council R3 ruling).
 *
 * Annotation row above the chart:
 *   "Today's estimate: 2750 kcal • 7-day adjustment: +15 kcal/day"
 *
 * testTags: `expenditure-chart-canvas`, `expenditure-chart-today-estimate`,
 *   `expenditure-chart-target-line`.
 */
@Composable
fun AdaptiveExpenditureChart(
    summary: ExpenditureSummary,
    targetIntakeKcal: Int,
) {
    val s = strings()
    val today = ExpenditureEstimator.roundKcal(summary.todayEstimate)
    val adjustment = ExpenditureEstimator.roundKcal(summary.adjustmentVsPrior)
    val adjustmentLabel = (if (adjustment >= 0) "+" else "") + adjustment.toString()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .testTag("expenditure-chart-card"),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = s.expenditure_chart_title,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "${s.expenditure_chart_today}: $today kcal · " +
                    "${s.expenditure_chart_adjustment}: $adjustmentLabel kcal/d",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("expenditure-chart-today-estimate"),
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(top = 8.dp)
                    .testTag("expenditure-chart-canvas"),
            ) {
                drawExpenditureSeries(
                    series = summary.series,
                    targetIntake = targetIntakeKcal.toDouble(),
                )
            }

            // The target-intake line is drawn inside the canvas; the testTag-bearing
            // Text caption below acts as the addressable hook for that line.
            Text(
                text = "${s.expenditure_chart_target_line}: $targetIntakeKcal kcal",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("expenditure-chart-target-line"),
            )
        }
    }
}

/**
 * Draw the TDEE series + target intake line. All neutral palette tokens —
 * no red/green semantics.
 */
private fun DrawScope.drawExpenditureSeries(
    series: List<Double>,
    targetIntake: Double,
) {
    if (series.isEmpty()) return
    val w = size.width
    val h = size.height
    val minV = min(series.min(), targetIntake) - 100.0
    val maxV = max(series.max(), targetIntake) + 100.0
    val range = (maxV - minV).coerceAtLeast(1.0)
    val stepX = if (series.size > 1) w / (series.size - 1).toFloat() else w

    fun y(v: Double): Float =
        (h - ((v - minV) / range * h).toFloat()).coerceIn(0f, h)

    // Target intake line (dashed)
    val targetY = y(targetIntake)
    drawLine(
        color = NeutralChip.foregroundLight,
        start = Offset(0f, targetY),
        end = Offset(w, targetY),
        strokeWidth = 1.5f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f),
    )

    // TDEE series line
    for (i in 0 until series.size - 1) {
        drawLine(
            color = NeutralChip.foregroundLight,
            start = Offset(i * stepX, y(series[i])),
            end = Offset((i + 1) * stepX, y(series[i + 1])),
            strokeWidth = 3f,
        )
    }

    // Series dots
    for ((i, v) in series.withIndex()) {
        drawCircle(
            color = NeutralChip.foregroundLight,
            radius = 3f,
            center = Offset(i * stepX, y(v)),
            style = Stroke(width = 1.5f),
        )
    }
}
