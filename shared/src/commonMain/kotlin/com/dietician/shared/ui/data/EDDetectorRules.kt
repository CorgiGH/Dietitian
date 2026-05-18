package com.dietician.shared.ui.data

/**
 * ED-safety detector rules — client-side defense-in-depth half of the Q8 design.
 *
 * Mirrors Plan-3 server-side detector with three signals:
 *   1. Kcal floor breach — under 80% of target for N consecutive days
 *   2. Weight-loss rate excessive — kg/week sustained
 *   3. Restrictive-pattern composite — derived from meal_event triggers
 *
 * Two tiers of action:
 *   - Hard refuse — STOP user from saving the unsafe target. UI surfaces
 *     [HardRefuseBanner] via `ed-rules-hard-refuse-banner` testTag.
 *   - Soft warn — show EDSafeguardModal but allow save. UI surfaces via
 *     `ed-rules-soft-warn-banner` testTag inside the modal.
 *
 * Q4 resolution: at friends-only first-ship, NO external resource links
 * (TelVerde / NEDA) are appended. The bigorexia-aware i18n copy from
 * [Strings.ed_safeguard_*] does the framing instead.
 */
object EDDetectorRules {

    /** Kcal target floor (hard refuse). Source: spec §6.9. */
    const val HARD_REFUSE_KCAL_FLOOR: Int = 1500

    /** Kcal target soft-warn threshold. */
    const val SOFT_WARN_KCAL_FLOOR: Int = 1800

    /** Weight-loss rate hard-refuse (kg/week). */
    const val HARD_REFUSE_WEIGHT_RATE_KG_WK: Double = 0.9

    /** Weight-loss rate soft-warn (kg/week). */
    const val SOFT_WARN_WEIGHT_RATE_KG_WK: Double = 0.5

    /** Days under 80% target before a kcal-floor-breach trip fires. */
    const val KCAL_FLOOR_DAYS_THRESHOLD: Int = 3

    /** Restrictive-pattern composite threshold (triggers per 7-day window). */
    const val RESTRICTIVE_PATTERN_THRESHOLD: Int = 5

    /** Evaluate a kcal target against hard-refuse + soft-warn floors. */
    fun checkKcalTarget(kcalTarget: Int): EDRuleVerdict = when {
        kcalTarget < HARD_REFUSE_KCAL_FLOOR -> EDRuleVerdict.HardRefuse
        kcalTarget < SOFT_WARN_KCAL_FLOOR -> EDRuleVerdict.SoftWarn
        else -> EDRuleVerdict.Allow
    }

    /** Evaluate a projected weight-loss rate against floors. */
    fun checkWeightRate(kgPerWeek: Double): EDRuleVerdict = when {
        kgPerWeek >= HARD_REFUSE_WEIGHT_RATE_KG_WK -> EDRuleVerdict.HardRefuse
        kgPerWeek >= SOFT_WARN_WEIGHT_RATE_KG_WK -> EDRuleVerdict.SoftWarn
        else -> EDRuleVerdict.Allow
    }

    /**
     * Body-comparison feature requests are unconditionally hard-refused — spec §6.9
     * ban list. UI surfaces a hard refuse banner.
     */
    fun checkBodyComparisonFeatureRequest(): EDRuleVerdict = EDRuleVerdict.HardRefuse
}

enum class EDRuleVerdict { Allow, SoftWarn, HardRefuse }

/**
 * Flag emitted by the detector hook when a safeguard modal should surface.
 *
 * Three variants matching the server-side detector signal set:
 *   1. KcalFloorBreach — sustained low-kcal pattern
 *   2. WeightRateExcessive — kg/week above cap (loss or gain — bigorexia symmetric)
 *   3. RestrictivePattern — composite restrictive-pattern detector signal
 */
sealed class EDFlag {
    abstract val severity: EDRuleVerdict

    /** Sustained kcal-floor breach. */
    data class KcalFloorBreach(
        val target: Int,
        val daysBelow80pct: Int,
        override val severity: EDRuleVerdict = EDRuleVerdict.SoftWarn,
    ) : EDFlag()

    /** Weight-loss rate above safe cap (symmetric: also gain above bigorexia cap). */
    data class WeightRateExcessive(
        val kgPerWeek: Double,
        override val severity: EDRuleVerdict = EDRuleVerdict.SoftWarn,
    ) : EDFlag()

    /** Restrictive-pattern composite detector signal. */
    data class RestrictivePattern(
        val detail: String,
        override val severity: EDRuleVerdict = EDRuleVerdict.SoftWarn,
    ) : EDFlag()
}

/** Snapshot of state used by [EDDetectorHook.shouldShowCheckIn]. */
data class EDState(
    val kcalTarget: Int = 2000,
    val daysBelow80pct: Int = 0,
    val weightRateKgPerWeek: Double = 0.0,
    val restrictivePatternTriggers: Int = 0,
    val plannedCutActive: Boolean = false,
)
