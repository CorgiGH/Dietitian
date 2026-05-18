package com.dietician.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.i18n.strings

/**
 * Vertical list of [PhotoSuggestionCard] rows with an RC11 escape-hatch at the
 * bottom: "None of these — type manually".
 *
 * Tapping the escape-hatch dismisses the suggestion list + opens a manual-entry
 * text field via [onNoneOfThese] callback. Per Q2 design the original photo
 * bytes remain in raw-store for re-OCR later if the user changes their mind.
 *
 * testTags: `photo-suggestion-list`, `photo-suggestion-none-of-these` (RC11),
 *   plus child component tags from [PhotoSuggestionCard].
 */
@Composable
fun PhotoSuggestionList(
    suggestions: List<PhotoSuggestion>,
    onConfirm: (PhotoSuggestion) -> Unit,
    onEdit: (PhotoSuggestion) -> Unit,
    onWrong: (PhotoSuggestion) -> Unit,
    onNoneOfThese: () -> Unit,
) {
    val s = strings()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .testTag("photo-suggestion-list"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((idx, suggestion) in suggestions.withIndex()) {
            PhotoSuggestionCard(
                suggestion = suggestion,
                index = idx,
                onConfirm = onConfirm,
                onEdit = onEdit,
                onWrong = onWrong,
            )
        }
        OutlinedButton(
            onClick = onNoneOfThese,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("photo-suggestion-none-of-these"),
        ) {
            Text(s.photo_suggestion_none_of_these)
        }
    }
}
