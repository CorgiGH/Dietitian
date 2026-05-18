package com.dietician.shared.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.data.WeeklyWeight
import com.dietician.shared.ui.data.WeightAggregator
import com.dietician.shared.ui.i18n.strings
import com.dietician.shared.ui.theme.NeutralChip
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Weight trend chart — Carbon-style weekly aggregate per spec §6.5.
 *
 * Counter-bigorexia primary surface: WEEKLY mean ONLY, not daily weight.
 * RC5 bigorexia framing copy "weekly aggregate, not daily weight" is the
 * card subtitle.
 *
 * Compose Canvas:
 *   - x = week index in [0, series.size)
 *   - y = mean kg per week
 *   - NEUTRAL palette only (no red/green)
 *
 * Toggles: 4 wk / 12 wk / 26 wk windows. Trend annotation is text only:
 *   "12-wk trend: +0.4 kg/wk (lean-bulk on target)"
 *
 * testTags: `weight-trend-card`, `weight-trend-canvas`,
 *   `weight-trend-toggle-4wk` / `-12wk` / `-26wk`, `weight-trend-summary`.
 */
@Composable
fun WeightTrendChart(
    series: List<WeeklyWeight>,
    window: WeightTrendWindow,
    onWindowChange: (WeightTrendWindow) -> Unit,
) {
    val s = strings()
    val effective = WeightAggregator.lastNWeeks(series, window.weeks)
    val slope = WeightAggregator.trendKgPerWeek(effective)
    val slopeLabel = formatSlope(slope)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .testTag("weight-trend-card"),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = s.weight_trend_title,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = s.bigorexia_weekly_aggregate,
                style = MaterialTheme.typography.bodySmall,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    selected = window == WeightTrendWindow.WEEKS_4,
                    onClick = { onWindowChange(WeightTrendWindow.WEEKS_4) },
                    label = { Text(s.weight_trend_toggle_4wk) },
                    modifier = Modifier.testTag("weight-trend-toggle-4wk"),
                )
                FilterChip(
                    selected = window == WeightTrendWindow.WEEKS_12,
                    onClick = { onWindowChange(WeightTrendWindow.WEEKS_12) },
                    label = { Text(s.weight_trend_toggle_12wk) },
                    modifier = Modifier.testTag("weight-trend-toggle-12wk"),
                )
                FilterChip(
                    selected = window == WeightTrendWindow.WEEKS_26,
                    onClick = { onWindowChange(WeightTrendWindow.WEEKS_26) },
                    label = { Text(s.weight_trend_toggle_26wk) },
                    modifier = Modifier.testTag("weight-trend-toggle-26wk"),
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(top = 8.dp)
                    .testTag("weight-trend-canvas"),
            ) {
                drawWeightSeries(effective)
            }

            Text(
                text = "${window.weeks}-wk ${s.weight_trend_summary_prefix}: $slopeLabel",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("weight-trend-summary"),
            )
        }
    }
}

/** Window selector for the trend chart. */
enum class WeightTrendWindow(val weeks: Int) {
    WEEKS_4(4),
    WEEKS_12(12),
    WEEKS_26(26),
}

/**
 * Format the kg/wk slope. NO red/green — text only, sign included so the
 * direction is unambiguous.
 */
private fun formatSlope(slope: Double): String {
    val sign = if (slope >= 0) "+" else ""
    val rounded = (slope * 100).toInt() / 100.0
    return "${sign}${rounded} kg/wk"
}

private fun DrawScope.drawWeightSeries(series: List<WeeklyWeight>) {
    if (series.isEmpty()) return
    val w = size.width
    val h = size.height
    val ys = series.map { it.meanKg }
    val minY = ys.min() - 1.0
    val maxY = ys.max() + 1.0
    val range = (maxY - minY).coerceAtLeast(0.5)
    val stepX = if (series.size > 1) w / (series.size - 1).toFloat() else w

    fun y(v: Double): Float =
        (h - ((v - minY) / range * h).toFloat()).coerceIn(0f, h)

    // Series line
    for (i in 0 until series.size - 1) {
        drawLine(
            color = NeutralChip.foregroundLight,
            start = Offset(i * stepX, y(series[i].meanKg)),
            end = Offset((i + 1) * stepX, y(series[i + 1].meanKg)),
            strokeWidth = 3f,
        )
    }
    // Series dots
    for ((i, v) in series.withIndex()) {
        drawCircle(
            color = NeutralChip.foregroundLight,
            radius = 4f,
            center = Offset(i * stepX, y(v.meanKg)),
            style = Stroke(width = 2f),
        )
    }
}

@Suppress("unused")
private fun unusedKeep() {
    // keep helper math available for future bigorexia symmetry display.
    val _x = abs(0.0)
    val _m = min(0.0, 1.0)
    val _M = max(0.0, 1.0)
}
