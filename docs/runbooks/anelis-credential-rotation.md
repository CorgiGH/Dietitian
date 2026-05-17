# Runbook 9 — Anelis Plus credential rotation

**Symptom:** Paper fetch jobs failing with `AuthRequired` or `SessionExpired`. `credential_heartbeat` table shows `anelis: status=broken`.

**Cause:**
- UAIC password changed (mandatory rotation periodically)
- Anelis session cookie expired
- Anelis backend changed auth scheme (rare but possible)

**Note:** Anelis auth model is TBD as of spec date (2026-05-17). Investigation gated for impl phase. Flow below assumes simple portal model; SAML/Shibboleth model would differ.

**User action (portal model — assumed):**
1. In-app: `/credentials rotate anelis`
2. Browser flow opens: log into Anelis with current UAIC credentials
3. Daemon captures session cookie, encrypts via Jagged age, stores via DPAPI/Keystore
4. Verify: `/credentials test anelis` returns OK
5. Pending paper-fetch jobs retried automatically

**User action (Shibboleth/SAML model — if detected):**
1. Manual session export weekly via desktop browser:
   - Log into Anelis via browser
   - Use browser extension or DevTools to export session cookies
   - Save to `state/anelis-session.json`
2. Daemon re-reads on next paper-fetch attempt

**Prevention:**
- `credential_heartbeat` warns when `last_success_at > 14d ago`
- Anelis investigation outcome documented in spec §13 once known
- If rotation gets frequent: consider deferring Anelis automation, falling back to manual paste
