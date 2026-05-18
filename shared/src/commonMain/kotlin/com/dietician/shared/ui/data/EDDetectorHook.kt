package com.dietician.shared.ui.data

/**
 * Client-side ED-detector hook (defense-in-depth half of Q8). Pure-function
 * façade — host wires this to meal_event / weight_event ledger insert in
 * Batch E Task 27.
 *
 * RC14 (planned-cut active state) overrides SOFT warnings ONLY — hard refuses
 * always trip regardless of planned-cut. Reasoning: planned-cut is a deliberate
 * short-window decision; bigorexia-clinical floors (kcal < 1500, loss > 0.9
 * kg/wk) sit BELOW that decision plane and are non-negotiable.
 *
 * Returns the highest-severity flag if any rule trips, else null. Order of
 * priority: kcal-floor-breach > weight-rate > restrictive-pattern.
 */
object EDDetectorHook {

    fun shouldShowCheckIn(state: EDState): EDFlag? {
        val kcalVerdict = if (state.daysBelow80pct >= EDDetectorRules.KCAL_FLOOR_DAYS_THRESHOLD) {
            EDDetectorRules.checkKcalTarget(state.kcalTarget)
        } else {
            EDRuleVerdict.Allow
        }

        val weightVerdict = EDDetectorRules.checkWeightRate(state.weightRateKgPerWeek)
        val restrictiveVerdict =
            if (state.restrictivePatternTriggers >= EDDetectorRules.RESTRICTIVE_PATTERN_THRESHOLD) {
                EDRuleVerdict.SoftWarn
            } else {
                EDRuleVerdict.Allow
            }

        val candidates = listOfNotNull(
            kcalVerdict.toFlag {
                EDFlag.KcalFloorBreach(
                    target = state.kcalTarget,
                    daysBelow80pct = state.daysBelow80pct,
                    severity = it,
                )
            },
            weightVerdict.toFlag {
                EDFlag.WeightRateExcessive(
                    kgPerWeek = state.weightRateKgPerWeek,
                    severity = it,
                )
            },
            restrictiveVerdict.toFlag {
                EDFlag.RestrictivePattern(
                    detail = "${state.restrictivePatternTriggers} triggers in window",
                    severity = it,
                )
            },
        )

        if (candidates.isEmpty()) return null

        // Hard refuses always surface, ignoring planned-cut.
        val hard = candidates.firstOrNull { it.severity == EDRuleVerdict.HardRefuse }
        if (hard != null) return hard

        // Soft warns: skip if planned-cut is active (RC14).
        if (state.plannedCutActive) return null
        return candidates.first()
    }

    private inline fun EDRuleVerdict.toFlag(builder: (EDRuleVerdict) -> EDFlag): EDFlag? =
        if (this == EDRuleVerdict.Allow) null else builder(this)
}
