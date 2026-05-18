package com.dietician.shared.ui.components

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.dietician.shared.ui.i18n.strings

/**
 * "Ingest from URL" CTA used in CookbookScreen header.
 *
 * Tap opens [com.dietician.shared.ui.screens.CookbookScreen]'s dialog where
 * the user pastes a recipe URL. POST to Plan-3 `/jobs/queue` happens via
 * [com.dietician.shared.ui.screens.CookbookViewModel.submitIngest].
 *
 * testTag: cookbook-ingest-url-button.
 */
@Composable
fun IngestUrlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.testTag("cookbook-ingest-url-button"),
    ) {
        Text(s.cookbook_ingest_from_url_button)
    }
}
