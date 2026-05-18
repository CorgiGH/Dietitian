package com.dietician.shared.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.i18n.strings

/**
 * 2-state magic-link onboarding surface — `EmailEntry` -> `CheckEmail`.
 *
 * State transitions:
 * - `EmailEntry`: user types email + taps "Send magic link" → caller invokes
 *   [OnboardingActions.onSendMagicLink], on success we rotate to `CheckEmail`.
 * - `CheckEmail`: shows the RC20 same-device copy + resend link CTA. The caller is
 *   expected to mount a [CrossDeviceVerifyListener] in parallel; when its `Verified`
 *   event fires, [OnboardingActions.onVerified] is called → caller navigates to Home.
 *
 * testTag selectors per Batch B brief:
 * - `onboarding-email-input`, `onboarding-send-magic-link`, `onboarding-check-email`,
 *   `onboarding-same-device-copy`, `onboarding-resend-link`, `onboarding-success`.
 *
 * Magic-link deep-link verification + actual WebSocket subscription is wired by the
 * platform shell (Batch E Task 27 — intent-filter on Android + custom URL handler on
 * Desktop). For Batch B the screen is callable end-to-end with mock callbacks.
 */
@Composable
fun OnboardingScreen(actions: OnboardingActions) {
    val s = strings()
    var email by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf<OnboardingStage>(OnboardingStage.EmailEntry) }
    var success by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("onboarding-screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        when (val stg = stage) {
            is OnboardingStage.EmailEntry -> {
                Text(text = s.onboarding_sign_in_title)
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(s.onboarding_email_label) },
                    modifier = Modifier.testTag("onboarding-email-input"),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        actions.onSendMagicLink(email)
                        stage = OnboardingStage.CheckEmail(email)
                    },
                    modifier = Modifier.testTag("onboarding-send-magic-link"),
                ) {
                    Text(s.onboarding_send_magic_link_button)
                }
            }

            is OnboardingStage.CheckEmail -> {
                Text(
                    text = s.onboarding_check_email_title,
                    modifier = Modifier.testTag("onboarding-check-email"),
                )
                Text(
                    text = s.onboarding_same_device_copy,
                    modifier = Modifier.testTag("onboarding-same-device-copy"),
                )
                TextButton(
                    onClick = { actions.onResend(stg.email) },
                    modifier = Modifier.testTag("onboarding-resend-link"),
                ) {
                    Text(s.onboarding_resend_link_button)
                }
                // Dev affordance — manually fire the verify signal that the real
                // WebSocket listener fires when the magic link is opened. Drives
                // smoke walks without a deployed backend. Hidden once the real
                // verify path lands (Plan-3 deploy + cross-device WS wiring).
                Button(
                    onClick = actions::onVerified,
                    modifier = Modifier.testTag("onboarding-simulate-verify"),
                ) {
                    Text(s.onboarding_simulate_verify_button)
                }
                if (success) {
                    Text(
                        text = s.onboarding_success_label,
                        modifier = Modifier.testTag("onboarding-success"),
                    )
                }
            }
        }
    }

    // Caller is expected to drive `actions.onVerified` from a Verify listener; when
    // it fires, flip a local flag so the success selector paints.
    actions.bindVerifiedSignal { success = true }
}

/** Onboarding stages. */
sealed class OnboardingStage {
    data object EmailEntry : OnboardingStage()
    data class CheckEmail(val email: String) : OnboardingStage()
}

/**
 * Callbacks the screen needs from the host (test harness or platform shell).
 *
 * Kept as a plain interface (not a ViewModel) so the screen stays a pure render
 * function — easier to drive in Compose UI test.
 */
interface OnboardingActions {
    /** Invoked when "Send magic link" is tapped. Host calls [AuthRepository.requestMagicLink]. */
    fun onSendMagicLink(email: String)

    /** Invoked when "Resend link" is tapped on the CheckEmail screen. */
    fun onResend(email: String)

    /** Invoked when WS [VerifyEvent.Verified] fires → screen surfaces success + caller navigates. */
    fun onVerified()

    /**
     * Binds the supplied callback to whatever signal the host uses (typically a
     * Flow collector started in a LaunchedEffect). Called once per composition.
     */
    fun bindVerifiedSignal(callback: () -> Unit)
}
