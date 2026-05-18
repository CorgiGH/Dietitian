# Smoke checklist — Plan-4-5 KMP Compose UI live verification

**Source of truth:** Plan-4-5 post-impl council 1779128340 must-fix items (a) + (b).

**Purpose:** Manual walk-through of the user-facing surfaces shipped by Plan-4-5. Required before the `plan-4-5/kmp-ui-foundation` PR merges to master. Component-isolation tests are green (434 desktopTest + 359 testDebugUnitTest) — this checklist closes the "shipped at component layer ≠ paints in real app" gap from CLAUDE.md's Feature-shipped verification rule.

---

## Desktop walk (council must-fix #a)

**Setup:**
```bash
cd C:/Users/User/Desktop/Dietician
./gradlew :desktopApp:run
```

**Walk path:** the app should open to Onboarding (first launch) OR Home (returning user).

### Onboarding screen
- [ ] AI literacy banner visible (`home-ai-literacy-banner` testTag) with the title "Dietician uses AI to suggest meals + parse receipts"
- [ ] Consent rows visible: "Process my meal, weight, and voice-memo data (Art 9 health-data)" + "Transfer my data to Anthropic / Google / OpenRouter (US) under SCC + DPF"
- [ ] Both consent toggles default OFF; tapping each persists to local SQLite (verify via SQLDelight inspector or restart-and-check)
- [ ] "Continue" button enabled only when Art-9 consent is granted

### Home screen
- [ ] SubjectCard renders with display name + truncated subject ID + Settings button
- [ ] AdaptiveExpenditurePreview card visible with title "Building your estimate" and body "Log meals and weigh-ins for 7 days — your adaptive TDEE appears here." (NOT the prior "TDEE today — coming with Batch D chart" TODO copy)
- [ ] TodayNutrientsCard with kcal / protein / carbs / fat chips in NEUTRAL color (no red/green)
- [ ] PlannedCutToggle row visible
- [ ] "Log a meal" CTA button at bottom

### FoodLog screen (tap "Log a meal" CTA)
- [ ] 5 entry modals reachable from buttons: text entry, voice note (RC1 fallback toast — "Voice transcription coming in next update"), photo, barcode, paper-search lookup
- [ ] Nutrient bars across 84-nutrient catalog render (top bars visible: kcal, protein, fat, carbs)
- [ ] Back button returns to Home

### CoachChat screen (tap Coach nav tab)
- [ ] Per-call Art-13 disclosure banner visible above the chat input (RC7)
- [ ] If coach is disabled (Settings → Privacy off): the disabled-notice from `coach_disabled_notice` Strings key shows instead of the chat surface (RC9)
- [ ] Audit deep-link from any sent message lands on AuditLog with the corresponding row highlighted

### AuditLog screen (tap Settings → Privacy → "View audit log" OR Coach disclosure → audit deep-link)
- [ ] "Audit log" title visible
- [ ] If empty: "No audit entries yet." copy renders
- [ ] After a coach message + 1 receipt upload: at least 2 rows visible with timestamp + action + subject_id columns
- [ ] Export buttons visible: "Export PDF" / "Export JSON" / "DSAR ZIP"
- [ ] Tapping any export saves a file to the OS file-dialog destination + emits an audit row of its own

### Failure protocol
If any checkbox fails: capture the failing screen (Win+Shift+S), stop the smoke, paste the screenshot + the failing testTag into a comment on the Plan-4-5 PR. Do NOT merge.

---

## Android walk (council must-fix #b)

**Setup:**
```bash
cd C:/Users/User/Desktop/Dietician
./gradlew :androidApp:installDebug
# Or sideload the APK from androidApp/build/outputs/apk/debug/androidApp-debug.apk via Android Studio's "Run on connected device".
```

**Walk path:** focus is MediaStore receipt-save. Other surfaces are the same as Desktop and only need a sanity sweep on Android.

### Receipt upload via camera (council must-fix #b)
- [ ] Open the app → FoodLog → Photo entry → "Photo of a receipt"
- [ ] CameraX preview opens (front-facing rejected automatically; rear camera only)
- [ ] Take a photo of a Mega Image / Carrefour receipt
- [ ] Tap "Save"
- [ ] Image lands in `Pictures/Dietician/` on the device — verify via the device's Files app or `adb shell ls /sdcard/Pictures/Dietician/`
- [ ] An entry appears in AuditLog with `action='receipt_uploaded'` + the local URI
- [ ] If offline: the entry is queued in the outbox (visible in `/diag` once online + synced) — receipt image stays in `Pictures/Dietician/` regardless

### Sanity sweep (Android-only quirks)
- [ ] System back button on every screen returns to the prior nav entry (no force-close, no skip to Home)
- [ ] Tailscale-disconnected blocker (RC16): toggle Tailscale OFF in the Tailscale Android app → re-open Dietician → blocker screen with title "Connect to Tailscale to use Dietician" renders, "Retry" button works
- [ ] Atkinson Hyperlegible font toggle (Settings → Accessibility): switching it on/off re-renders all text without app restart
- [ ] EDSafeguardModal (Task 18): set weight loss target below the floor → hard-refuse copy "This target is unsafe and cannot be saved. Adjust upward." renders + the target does NOT persist

### Failure protocol
Same as Desktop. Capture + comment + do NOT merge.

---

## Post-walk

After both walks pass, comment on the Plan-4-5 PR:
> Live smoke walks complete on Desktop + Android device. All checklist items green. Approved for merge.

Then proceed with the dep-ordered merge sequence:
1. `plan-1/shared-data` → master
2. `plan-3/server-first-batch-migrations` → master
3. `plan-2/shared-llm-batch-a` → master (rebase if needed)
4. `plan-4-5/kmp-ui-foundation` → master

---

## Why these manual walks instead of more Compose UI tests

Per CLAUDE.md "Feature-shipped verification rule" + the 2026-05-11 Slice 1 ghost-component lesson: Compose UI tests assert that a component renders given a `ComposeContentTestRule` mount. They do NOT prove that the navigation graph wires the component into the live app, that the platform-specific shell (Android `MainActivity` / Desktop `application { Window { ... } }`) hosts the right composable tree, or that real MediaStore / Clipboard / FileDialog system services work.

A `:desktopApp:run` walk and an Android device install bypass the mocking layer entirely — they are the smallest test that proves the feature ships.
