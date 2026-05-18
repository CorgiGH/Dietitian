package com.dietician.shared.ui.components

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.dietician.shared.ui.i18n.strings

/**
 * AI Act Art 14 "Just tell me" button. Bypasses [com.dietician.shared.llm.LlmStream]
 * and returns a deterministic rule-based answer derived from local pantry +
 * recent meals.
 *
 * First-ship returns a static plausible answer; Plan-3 Task 39 + Plan-7 corpus
 * replace this with a real local-corpus rule-based answerer. The bypass path
 * always writes a `just_tell_me_bypass` audit row.
 *
 * testTag: coach-just-tell-me-button.
 */
@Composable
fun JustTellMeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.testTag("coach-just-tell-me-button"),
    ) {
        Text(s.coach_just_tell_me_button)
    }
}
