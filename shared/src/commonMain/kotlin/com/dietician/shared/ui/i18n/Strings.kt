/*
 * i18n strings — EN + RO.
 *
 * Direct sealed-object pattern (no Compose stringResource). Lookup via
 * [LocaleProvider.current.strings] at runtime; tests reach Strings_en /
 * Strings_ro directly.
 *
 * Locked copy assertions (RC5 BigorexiaCopyTest) — DO NOT edit the bigorexia_*
 * phrases without updating the test.
 */

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
