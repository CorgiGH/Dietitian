package com.dietician.shared.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Shown after the user taps a magic-link deep-link
 * (e.g. `https://dietician.<tail-net>/auth/verify?token=...`).
 *
 * The host decodes the URL, extracts `token`, and calls [AuthRepository.verifyMagicLink].
 * The screen renders one of three states based on [state]:
 * - [VerifyUiState.Loading]: spinner + `magic-link-verify-loading`
 * - [VerifyUiState.Success]: success label + auto-navigate hook
 *   (`magic-link-verify-success`)
 * - [VerifyUiState.Error]: error + back-to-onboarding link
 *   (`magic-link-verify-error` + `magic-link-back-to-onboarding`)
 *
 * No ViewModel held here; the host owns the [AuthRepository] + the `Result<Session>` and
 * passes the mapped state in. Compose UI test asserts the right tag paints per state.
 */
@Composable
fun MagicLinkVerifyScreen(
    state: VerifyUiState,
    onBackToOnboarding: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("magic-link-verify-screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            VerifyUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.testTag("magic-link-verify-loading"),
                )
                Text("Verifying your magic link…")
            }

            VerifyUiState.Success -> {
                Text(
                    text = "Signed in — taking you home.",
                    modifier = Modifier.testTag("magic-link-verify-success"),
                )
            }

            is VerifyUiState.Error -> {
                Text(
                    text = state.message,
                    modifier = Modifier.testTag("magic-link-verify-error"),
                )
                TextButton(
                    onClick = onBackToOnboarding,
                    modifier = Modifier.testTag("magic-link-back-to-onboarding"),
                ) {
                    Text("Back to sign-in")
                }
            }
        }
    }
}

/** Display-state for [MagicLinkVerifyScreen]. */
sealed class VerifyUiState {
    data object Loading : VerifyUiState()
    data object Success : VerifyUiState()
    data class Error(val message: String) : VerifyUiState()

    companion object {
        /** Maps an [AuthError] to a localized error message. */
        fun fromAuthError(error: AuthError): Error =
            when (error) {
                AuthError.InvalidToken ->
                    Error("Magic link expired or invalid — request a new one.")
                is AuthError.Server -> Error("Server returned ${error.statusCode}. Try again later.")
                is AuthError.Network -> Error("Network error: ${error.detail}")
            }
    }
}
