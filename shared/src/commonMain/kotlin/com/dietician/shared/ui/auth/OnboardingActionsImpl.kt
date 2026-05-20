package com.dietician.shared.ui.auth

import com.dietician.shared.ui.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Onboarding actions wiring the magic-link flow.
 *
 * Real Plan-3 path:
 *   1. user types email → [AuthRepository.requestMagicLink] POST /auth/magic-link/request
 *   2. Desktop has no email client / deep-link handler — the user pastes the
 *      token from the magic-link into the CheckEmail token field →
 *      [onVerifyToken] → [AuthRepository.verifyMagicLink] POST /auth/magic-link/verify
 *   3. /verify returns a Session → [SessionStore] holds it → the HTTP client's
 *      SessionInterceptor attaches the cookie to every authed call (Coach 2PC,
 *      sync, etc.) → [SettingsStore.markOnboarded] flips the gate.
 *
 * When [authRepository] is null (tests / no network) the impl degrades to the
 * simulate-only behaviour: `onSendMagicLink` is a no-op and the "Simulate
 * verify" button drives [onVerified] WITHOUT a server session.
 */
class OnboardingActionsImpl(
    private val settingsStore: SettingsStore,
    private val authRepository: AuthRepository? = null,
    private val scope: CoroutineScope? = null,
    private val onSendSimulated: (String) -> Unit = {},
) : OnboardingActions {

    private var verifiedCallback: () -> Unit = {}

    override fun onSendMagicLink(email: String) {
        onSendSimulated(email)
        val repo = authRepository
        val sc = scope
        if (repo != null && sc != null) {
            sc.launch { repo.requestMagicLink(email) }
        }
    }

    override fun onResend(email: String) {
        onSendMagicLink(email)
    }

    override fun onVerified() {
        settingsStore.markOnboarded()
        verifiedCallback()
    }

    override fun onVerifyToken(token: String, onResult: (Boolean) -> Unit) {
        val repo = authRepository
        val sc = scope
        if (token.isBlank()) {
            onResult(false)
            return
        }
        if (repo == null || sc == null) {
            // No network wiring (tests) — fall back to the simulate behaviour.
            onVerified()
            onResult(true)
            return
        }
        sc.launch {
            repo.verifyMagicLink(token)
                .onSuccess {
                    settingsStore.markOnboarded()
                    verifiedCallback()
                    onResult(true)
                }
                .onFailure { onResult(false) }
        }
    }

    override fun bindVerifiedSignal(callback: () -> Unit) {
        verifiedCallback = callback
    }
}
