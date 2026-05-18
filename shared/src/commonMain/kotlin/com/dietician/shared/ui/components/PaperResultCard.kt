package com.dietician.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.data.PaperResult
import com.dietician.shared.ui.i18n.strings

/**
 * Search-result row card. Title + abstract snippet + score + "Open detail"
 * affordance. Open-detail is a 501-stub for first-ship (full detail screen
 * lands in Plan-7 + Batch D when paper corpus is ingested).
 */
@Composable
fun PaperResultCard(
    result: PaperResult,
    index: Int,
    onOpenDetail: (String) -> Unit,
) {
    val s = strings()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("paper-result-$index"),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = result.abstractSnippet,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "score: ${formatScore(result.score)}",
                    style = MaterialTheme.typography.labelSmall,
                )
                TextButton(onClick = { onOpenDetail(result.id) }) {
                    Text(s.paper_result_open_detail_button)
                }
            }
        }
    }
}

private fun formatScore(s: Double): String {
    val pct = (s * 1000).toInt() / 10.0
    return "$pct%"
}
