/*
 * i18n strings — EN + RO.
 *
 * Direct sealed-object pattern (no Compose stringResource). Lookup via
 * [LocaleProvider.current.strings] at runtime; tests reach Strings_en /
 * Strings_ro directly.
 *
 * Locked copy assertions (RC5 BigorexiaCopyTest) — DO NOT edit the bigorexia_*
 * phrases without updating the test.
 *
 * Snake_case property names mirror Android `strings.xml` keys so identifiers
 * roundtrip into moko-resources / XML without renaming. ktlint's
 * `property-naming` rule wants camelCase — suppress at file level.
 */

@file:Suppress("ktlint:standard:property-naming", "ktlint:standard:class-naming", "ClassName")

package com.dietician.shared.ui.i18n

/** Strings interface — every key exists in both locales. */
interface Strings {
    // --- Bigorexia framing (RC5 locked) ---
    val bigorexia_strength_focus: String
    val bigorexia_weekly_aggregate: String
    val bigorexia_process_target: String

    // --- AI literacy banner (Art 4 — RC18) ---
    val ai_literacy_banner_title: String
    val ai_literacy_banner_disclaimer: String

    // --- Photo suggestion (RC11) ---
    val photo_suggestion_none_of_these: String

    // --- BYOK clipboard (RC13) ---
    val byok_clipboard_cleared: String

    // --- Tailscale disconnected (RC16) ---
    val tailscale_disconnected_title: String
    val tailscale_disconnected_body: String

    // --- Voice fallback (RC1) ---
    val voice_coming_soon: String

    // --- LLM coach disabled (RC9) ---
    val coach_disabled_notice: String

    // --- Planned cut toggle (RC14) ---
    val planned_cut_toggle: String

    // --- Navigation labels (Task 4 consumers) ---
    val nav_home: String
    val nav_food_log: String
    val nav_pantry: String
    val nav_coach: String
    val nav_settings: String

    // --- AuditLog screen (Art 12 + Art 15 — Task 16) ---
    val audit_log_title: String
    val audit_log_empty: String
    val audit_log_emotion_disabled_badge: String
    val audit_log_export_pdf: String
    val audit_log_export_json: String
    val audit_log_export_dsar: String
    val audit_log_export_saved: String
    val audit_log_export_failed: String

    // --- Consent rows (RC19 — Art 9 + SCC/DPF) ---
    val consent_art9_health_label: String
    val consent_cross_border_label: String
    val consent_granted_at: String
    val consent_withdrawn_at: String

    // --- ED safeguard modal (Task 18) ---
    val ed_safeguard_title: String
    val ed_safeguard_body: String
    val ed_safeguard_adjust_target: String
    val ed_safeguard_planned_cut: String
    val ed_safeguard_pause_tracking: String
    val ed_safeguard_dismiss: String
    val ed_rules_hard_refuse: String
    val ed_rules_soft_warn: String

    // --- Adaptive expenditure chart (Task 19) ---
    val expenditure_chart_title: String
    val expenditure_chart_today: String
    val expenditure_chart_adjustment: String
    val expenditure_chart_target_line: String

    // --- Home expenditure-preview empty state (Plan-4-5 post-impl council fix) ---
    val expenditure_preview_empty_title: String
    val expenditure_preview_empty_body: String

    // --- Home + FoodLog buttons + cards (nav-mount-iter-3 i18n batch) ---
    val home_log_meal_cta: String
    val today_nutrients_title: String
    val foodlog_manual_field_label: String
    val foodlog_voice_button: String
    val foodlog_barcode_button: String
    val foodlog_photo_button: String
    val foodlog_same_as_recent_button: String

    // --- FoodLog iter-4 sub-buttons (barcode toast + same-as-recent sheet) ---
    val foodlog_barcode_coming_soon: String
    val foodlog_same_as_recent_empty_title: String
    val foodlog_same_as_recent_empty_body: String

    // --- Pantry + Coach + PlannedCut + AILiteracy (iter 3 follow-up batch 2) ---
    val pantry_empty_state: String
    val pantry_add_manually_fab: String
    val planned_cut_days_remaining_suffix: String
    val coach_screen_title: String
    val coach_input_placeholder: String
    val coach_send_button: String
    val coach_cancel_button: String
    val coach_just_tell_me_button: String
    val coach_re_enable_in_settings_button: String
    val ai_literacy_understood_button: String

    // --- Settings screen (nav-mount-iter-3) ---
    val settings_language_label: String
    val settings_locale_en_label: String
    val settings_locale_ro_label: String
    val settings_dark_theme_label: String
    val settings_accessible_typography_label: String
    val settings_coach_disabled_label: String
    val settings_about_title: String
    val settings_about_version_prefix: String
    val settings_about_spec_prefix: String

    // --- Photo suggestion (Task 20 RC11 already in base, extend) ---
    val photo_suggestion_confirm: String
    val photo_suggestion_edit: String
    val photo_suggestion_wrong: String

    // --- BYOK screen (Task 20 RC13) ---
    val byok_title: String
    val byok_provider_label: String
    val byok_key_label: String
    val byok_save: String
    val byok_paste_detected: String

    // --- Weight trend chart (Task 22) ---
    val weight_trend_title: String
    val weight_trend_toggle_4wk: String
    val weight_trend_toggle_12wk: String
    val weight_trend_toggle_26wk: String
    val weight_trend_summary_prefix: String
}

/** English. */
object Strings_en : Strings {
    override val bigorexia_strength_focus: String =
        "Strength, energy, mood, sleep — not just weight"
    override val bigorexia_weekly_aggregate: String =
        "Weekly aggregate — not daily weight"
    override val bigorexia_process_target: String =
        "Process target, not body target"

    override val ai_literacy_banner_title: String =
        "Dietician uses AI to suggest meals + parse receipts"
    override val ai_literacy_banner_disclaimer: String =
        "AI suggestions are not medical advice. You can disable AI features in Settings → Privacy."

    override val photo_suggestion_none_of_these: String =
        "None of these — type manually"

    override val byok_clipboard_cleared: String =
        "Clipboard cleared for security"

    override val tailscale_disconnected_title: String =
        "Connect to Tailscale to use Dietician"
    override val tailscale_disconnected_body: String =
        "Dietician requires Tailscale to reach the VPS. Open Tailscale + retry."

    override val voice_coming_soon: String =
        "Voice transcription coming in next update — type your meal below."

    override val coach_disabled_notice: String =
        "AI coach disabled. Re-enable in Settings → Privacy."

    override val planned_cut_toggle: String =
        "I'm in a planned cut (7-day window)"

    override val nav_home: String = "Home"
    override val nav_food_log: String = "Food log"
    override val nav_pantry: String = "Pantry"
    override val nav_coach: String = "Coach"
    override val nav_settings: String = "Settings"

    override val audit_log_title: String = "Audit log"
    override val audit_log_empty: String = "No audit entries yet."
    override val audit_log_emotion_disabled_badge: String = "emotion inference disabled"
    override val audit_log_export_pdf: String = "Export PDF"
    override val audit_log_export_json: String = "Export JSON"
    override val audit_log_export_dsar: String = "DSAR ZIP"
    override val audit_log_export_saved: String = "Saved to"
    override val audit_log_export_failed: String = "Export failed"

    override val consent_art9_health_label: String =
        "Process my meal, weight, and voice-memo data (Art 9 health-data)"
    override val consent_cross_border_label: String =
        "Transfer my data to Anthropic / Google / OpenRouter (US) under SCC + DPF"
    override val consent_granted_at: String = "granted"
    override val consent_withdrawn_at: String = "withdrawn"

    override val ed_safeguard_title: String = "Some days have run low"
    override val ed_safeguard_body: String =
        "Anything we can adjust? Process target, not body target."
    override val ed_safeguard_adjust_target: String = "Adjust target"
    override val ed_safeguard_planned_cut: String = "I'm in a planned cut"
    override val ed_safeguard_pause_tracking: String = "Pause tracking"
    override val ed_safeguard_dismiss: String = "Dismiss"
    override val ed_rules_hard_refuse: String =
        "This target is unsafe and cannot be saved. Adjust upward."
    override val ed_rules_soft_warn: String =
        "Below recommended floor. Process target, not body target."

    override val expenditure_chart_title: String = "Adaptive expenditure"
    override val expenditure_chart_today: String = "Today's estimate"
    override val expenditure_chart_adjustment: String = "7-day adjustment"
    override val expenditure_chart_target_line: String = "Target intake"

    override val expenditure_preview_empty_title: String = "Building your estimate"
    override val expenditure_preview_empty_body: String =
        "Log meals and weigh-ins for 7 days — your adaptive TDEE appears here."

    override val home_log_meal_cta: String = "Log a meal"
    override val today_nutrients_title: String = "Today's nutrients"
    override val foodlog_manual_field_label: String = "Type a food or meal"
    override val foodlog_voice_button: String = "Record voice"
    override val foodlog_barcode_button: String = "Scan barcode"
    override val foodlog_photo_button: String = "Photo of meal"
    override val foodlog_same_as_recent_button: String = "Same as recent"

    override val foodlog_barcode_coming_soon: String =
        "Barcode scan ships with Plan-6 — type the food manually below."
    override val foodlog_same_as_recent_empty_title: String = "No recent meals yet"
    override val foodlog_same_as_recent_empty_body: String =
        "Log a few meals first — they'll appear here for one-tap re-log."

    override val pantry_empty_state: String = "Pantry is empty — tap + to add an item"
    override val pantry_add_manually_fab: String = "+ Add manually"
    override val planned_cut_days_remaining_suffix: String = "days remaining"
    override val coach_screen_title: String = "Coach"
    override val coach_input_placeholder: String = "Ask the coach..."
    override val coach_send_button: String = "Send"
    override val coach_cancel_button: String = "Cancel"
    override val coach_just_tell_me_button: String = "Just tell me"
    override val coach_re_enable_in_settings_button: String = "Re-enable in Settings"
    override val ai_literacy_understood_button: String = "I understand"

    override val settings_language_label: String = "Language"
    override val settings_locale_en_label: String = "English"
    override val settings_locale_ro_label: String = "Română"
    override val settings_dark_theme_label: String = "Dark theme"
    override val settings_accessible_typography_label: String =
        "Accessible typography (Atkinson Hyperlegible)"
    override val settings_coach_disabled_label: String = "Disable AI coach features"
    override val settings_about_title: String = "About"
    override val settings_about_version_prefix: String = "Dietician"
    override val settings_about_spec_prefix: String = "Spec"

    override val photo_suggestion_confirm: String = "Confirm"
    override val photo_suggestion_edit: String = "Edit"
    override val photo_suggestion_wrong: String = "Wrong"

    override val byok_title: String = "Bring your own key"
    override val byok_provider_label: String = "Provider"
    override val byok_key_label: String = "API key"
    override val byok_save: String = "Save"
    override val byok_paste_detected: String = "Paste detected"

    override val weight_trend_title: String = "Weight trend"
    override val weight_trend_toggle_4wk: String = "4 wk"
    override val weight_trend_toggle_12wk: String = "12 wk"
    override val weight_trend_toggle_26wk: String = "26 wk"
    override val weight_trend_summary_prefix: String = "trend"
}

/** Romanian. */
object Strings_ro : Strings {
    override val bigorexia_strength_focus: String =
        "Forță, energie, dispoziție, somn — nu doar greutate"
    override val bigorexia_weekly_aggregate: String =
        "Agregat săptămânal — nu cântar zilnic"
    override val bigorexia_process_target: String =
        "Țintă de proces, nu țintă corporală"

    override val ai_literacy_banner_title: String =
        "Dietician folosește AI pentru sugestii de mese + parsare bonuri"
    override val ai_literacy_banner_disclaimer: String =
        "Sugestiile AI nu constituie sfat medical. Poți dezactiva funcțiile AI în Setări → Confidențialitate."

    override val photo_suggestion_none_of_these: String =
        "Niciuna dintre acestea — scrie manual"

    override val byok_clipboard_cleared: String =
        "Clipboard-ul șters pentru securitate"

    override val tailscale_disconnected_title: String =
        "Conectează-te la Tailscale pentru a folosi Dietician"
    override val tailscale_disconnected_body: String =
        "Dietician are nevoie de Tailscale pentru a accesa serverul. Deschide Tailscale + reîncearcă."

    override val voice_coming_soon: String =
        "Transcrierea vocală vine în următoarea actualizare — scrie masa mai jos."

    override val coach_disabled_notice: String =
        "Coach-ul AI dezactivat. Reactivează din Setări → Confidențialitate."

    override val planned_cut_toggle: String =
        "Sunt într-o tăiere planificată (fereastră 7 zile)"

    override val nav_home: String = "Acasă"
    override val nav_food_log: String = "Jurnal mese"
    override val nav_pantry: String = "Cămară"
    override val nav_coach: String = "Coach"
    override val nav_settings: String = "Setări"

    override val audit_log_title: String = "Jurnal de audit"
    override val audit_log_empty: String = "Nicio intrare în audit deocamdată."
    override val audit_log_emotion_disabled_badge: String = "inferență emoție dezactivată"
    override val audit_log_export_pdf: String = "Exportă PDF"
    override val audit_log_export_json: String = "Exportă JSON"
    override val audit_log_export_dsar: String = "Arhivă DSAR"
    override val audit_log_export_saved: String = "Salvat la"
    override val audit_log_export_failed: String = "Export eșuat"

    override val consent_art9_health_label: String =
        "Procesează datele despre mese, greutate și note vocale (Art 9 date de sănătate)"
    override val consent_cross_border_label: String =
        "Transferă datele mele către Anthropic / Google / OpenRouter (SUA) sub SCC + DPF"
    override val consent_granted_at: String = "acordat"
    override val consent_withdrawn_at: String = "retras"

    override val ed_safeguard_title: String = "Câteva zile au fost scăzute"
    override val ed_safeguard_body: String =
        "E ceva de ajustat? Țintă de proces, nu țintă corporală."
    override val ed_safeguard_adjust_target: String = "Ajustează ținta"
    override val ed_safeguard_planned_cut: String = "Sunt într-o tăiere planificată"
    override val ed_safeguard_pause_tracking: String = "Pauză urmărire"
    override val ed_safeguard_dismiss: String = "Renunță"
    override val ed_rules_hard_refuse: String =
        "Această țintă nu e sigură și nu poate fi salvată. Ajustează în sus."
    override val ed_rules_soft_warn: String =
        "Sub pragul recomandat. Țintă de proces, nu țintă corporală."

    override val expenditure_chart_title: String = "Consum energetic adaptiv"
    override val expenditure_chart_today: String = "Estimare azi"
    override val expenditure_chart_adjustment: String = "Ajustare 7 zile"
    override val expenditure_chart_target_line: String = "Țintă aport"

    override val expenditure_preview_empty_title: String = "Estimarea ta se formează"
    override val expenditure_preview_empty_body: String =
        "Înregistrează mese și cântăriri timp de 7 zile — TDEE-ul tău adaptiv apare aici."

    override val home_log_meal_cta: String = "Înregistrează o masă"
    override val today_nutrients_title: String = "Nutrienți de astăzi"
    override val foodlog_manual_field_label: String = "Scrie o mâncare sau masă"
    override val foodlog_voice_button: String = "Înregistrare vocală"
    override val foodlog_barcode_button: String = "Scanează cod de bare"
    override val foodlog_photo_button: String = "Fotografie masă"
    override val foodlog_same_as_recent_button: String = "La fel ca recent"

    override val foodlog_barcode_coming_soon: String =
        "Scanarea codurilor vine cu Plan-6 — scrie mâncarea manual mai jos."
    override val foodlog_same_as_recent_empty_title: String = "Nicio masă recentă"
    override val foodlog_same_as_recent_empty_body: String =
        "Înregistrează câteva mese — vor apărea aici pentru re-logare rapidă."

    override val pantry_empty_state: String = "Cămara e goală — apasă + pentru a adăuga"
    override val pantry_add_manually_fab: String = "+ Adaugă manual"
    override val planned_cut_days_remaining_suffix: String = "zile rămase"
    override val coach_screen_title: String = "Coach"
    override val coach_input_placeholder: String = "Întreabă coach-ul..."
    override val coach_send_button: String = "Trimite"
    override val coach_cancel_button: String = "Anulează"
    override val coach_just_tell_me_button: String = "Doar spune-mi"
    override val coach_re_enable_in_settings_button: String = "Reactivează din Setări"
    override val ai_literacy_understood_button: String = "Am înțeles"

    override val settings_language_label: String = "Limbă"
    override val settings_locale_en_label: String = "English"
    override val settings_locale_ro_label: String = "Română"
    override val settings_dark_theme_label: String = "Temă întunecată"
    override val settings_accessible_typography_label: String =
        "Tipografie accesibilă (Atkinson Hyperlegible)"
    override val settings_coach_disabled_label: String = "Dezactivează funcțiile AI coach"
    override val settings_about_title: String = "Despre"
    override val settings_about_version_prefix: String = "Dietician"
    override val settings_about_spec_prefix: String = "Spec"

    override val photo_suggestion_confirm: String = "Confirmă"
    override val photo_suggestion_edit: String = "Editează"
    override val photo_suggestion_wrong: String = "Greșit"

    override val byok_title: String = "Cheia ta de API"
    override val byok_provider_label: String = "Furnizor"
    override val byok_key_label: String = "Cheie API"
    override val byok_save: String = "Salvează"
    override val byok_paste_detected: String = "Lipire detectată"

    override val weight_trend_title: String = "Tendință greutate"
    override val weight_trend_toggle_4wk: String = "4 săpt"
    override val weight_trend_toggle_12wk: String = "12 săpt"
    override val weight_trend_toggle_26wk: String = "26 săpt"
    override val weight_trend_summary_prefix: String = "tendință"
}

/** Supported locales. */
enum class AppLocale(val code: String, val strings: Strings) {
    EN(code = "en", strings = Strings_en),
    RO(code = "ro", strings = Strings_ro),
    ;

    companion object {
        fun fromTag(tag: String?): AppLocale =
            when (tag?.lowercase()?.substringBefore('-')) {
                "ro" -> RO
                "en" -> EN
                else -> EN
            }
    }
}
