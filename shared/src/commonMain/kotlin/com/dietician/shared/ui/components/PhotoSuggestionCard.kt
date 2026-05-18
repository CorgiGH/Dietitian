package com.dietician.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.i18n.strings

/**
 * Single photo-OCR suggestion card.
 *
 * Renders horizontally: thumbnail placeholder box (real bytes → ImageBitmap
 * wiring lands in Batch E platform shells) + suggested name + confidence%
 * + Confirm / Edit / Wrong action buttons.
 *
 * testTags:
 *   - `photo-suggestion-card-{idx}` on the card
 *   - `photo-suggestion-confirm-{idx}` / `-edit-{idx}` / `-wrong-{idx}`
 */
@Composable
fun PhotoSuggestionCard(
    suggestion: PhotoSuggestion,
    index: Int,
    onConfirm: (PhotoSuggestion) -> Unit,
    onEdit: (PhotoSuggestion) -> Unit,
    onWrong: (PhotoSuggestion) -> Unit,
) {
    val s = strings()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("photo-suggestion-card-$index"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Thumbnail placeholder — real ImageBitmap painter lands Batch E.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .testTag("photo-suggestion-thumb-$index"),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = suggestion.label,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "conf ${(suggestion.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(
                onClick = { onConfirm(suggestion) },
                modifier = Modifier.testTag("photo-suggestion-confirm-$index"),
            ) { Text(s.photo_suggestion_confirm) }
            TextButton(
                onClick = { onEdit(suggestion) },
                modifier = Modifier.testTag("photo-suggestion-edit-$index"),
            ) { Text(s.photo_suggestion_edit) }
            TextButton(
                onClick = { onWrong(suggestion) },
                modifier = Modifier.testTag("photo-suggestion-wrong-$index"),
            ) { Text(s.photo_suggestion_wrong) }
        }
    }
}

/** UI-layer photo OCR suggestion. */
data class PhotoSuggestion(
    val id: String,
    val label: String,
    val confidence: Double,
    val thumbnailBytes: ByteArray? = null,
) {
    @Suppress("RedundantOverride")
    override fun equals(other: Any?): Boolean = super.equals(other)

    @Suppress("RedundantOverride")
    override fun hashCode(): Int = super.hashCode()
}
