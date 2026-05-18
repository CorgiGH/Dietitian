package com.dietician.shared.ui.auth

import com.dietician.shared.ui.settings.SettingsStore

/**
 * Onboarding actions that simulate the magic-link flow without a live backend.
 *
 * Real Plan-3 path (when VPS deploys):
 *   1. user types email → [AuthRepository.requestMagicLink] POST /auth/magic-link/request
 *   2. user opens link in Gmail on same device → handler decodes token → [AuthRepository.verifyMagicLink] POST /auth/magic-link/verify
 *   3. /verify returns Session → [SessionStore] holds it → DieticianApp paints
 *
 * Current state: backend not deployed. This impl fakes step 1 (no network call) and
 * exposes a "Simulate verify" hook on the CheckEmail screen so smoke walks can drive
 * the full Onboarding → DieticianApp transition without a real magic link. On
 * verification, [SettingsStore.markOnboarded] flips `state.onboarded = true` →
 * DieticianApp gate stops rendering OnboardingScreen.
 *
 * When [AuthRepository] is registered in the Koin module the constructor can swap
 * over to real `requestMagicLink` calls; the verify-callback wiring stays the same.
 */
class OnboardingActionsImpl(
    private val settingsStore: SettingsStore,
    private val onSendSimulated: (String) -> Unit = {},
) : OnboardingActions {

    private var verifiedCallback: () -> Unit = {}

    override fun onSendMagicLink(email: String) {
        onSendSimulated(email)
        // No-op for now — real impl POSTs /auth/magic-link/request.
        // CheckEmail stage's "Simulate verify" CTA drives the transition.
    }

    override fun onResend(email: String) {
        onSendSimulated(email)
    }

    override fun onVerified() {
        settingsStore.markOnboarded()
        verifiedCallback()
    }

    override fun bindVerifiedSignal(callback: () -> Unit) {
        verifiedCallback = callback
    }
}
