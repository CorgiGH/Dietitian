package com.dietician.shared.ui.integration

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.dietician.shared.ui.components.EDSafeguardModal
import com.dietician.shared.ui.data.EDDetectorHook
import com.dietician.shared.ui.data.EDFlag
import com.dietician.shared.ui.data.EDRuleVerdict
import com.dietician.shared.ui.data.EDState
import com.dietician.shared.ui.i18n.AppLocale
import com.dietician.shared.ui.i18n.DieticianLocaleProvider
import com.dietician.shared.ui.theme.DieticianTheme
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plan-4-5 Task 33 — ED-detector → safeguard modal flow.
 *
 * Validates the spec §6.9 hard-refuse + soft-warn semantics through the public
 * detector hook + the modal Composable:
 *   1. kcal target < 1500 sustained 3d → HardRefuse flag emitted
 *   2. Modal renders hard-refuse banner
 *   3. Planned-cut RC14 cannot override hard-refuse
 *   4. Soft-warn (kcal 1500-1799) IS suppressed when planned-cut active
 *   5. Bigorexia-symmetric weight rate > 0.9 kg/wk → HardRefuse
 */
class EDDetectorFlowTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `kcal target below 1500 with 3+ days under 80pct trips HardRefuse and modal renders banner`() {
        val flag = EDDetectorHook.shouldShowCheckIn(
            EDState(
                kcalTarget = 1400,
                daysBelow80pct = 4,
                weightRateKgPerWeek = 0.0,
                plannedCutActive = false,
            ),
        )
        assertNotNull(flag, "expected ED flag for sub-1500 target")
        assertEquals(EDRuleVerdict.HardRefuse, flag.severity)
        assertTrue(flag is EDFlag.KcalFloorBreach)

        composeRule.setContent {
            DieticianLocaleProvider(locale = AppLocale.EN) {
                DieticianTheme {
                    EDSafeguardModal(
                        flag = flag,
                        plannedCutActive = false,
                        onAdjustTarget = {},
                        onPlannedCutToggle = {},
                        onPauseTracking = {},
                        onDismiss = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("ed-checkin-modal").assertIsDisplayed()
        composeRule.onNodeWithTag("ed-rules-hard-refuse-banner").assertIsDisplayed()
        composeRule.onNodeWithTag("ed-checkin-adjust-target").assertIsDisplayed()
    }

    @Test
    fun `RC14 planned-cut active does NOT suppress hard-refuse (kcal floor 1500)`() {
        val flag = EDDetectorHook.shouldShowCheckIn(
            EDState(
                kcalTarget = 1200,
                daysBelow80pct = 5,
                plannedCutActive = true,
            ),
        )
        assertNotNull(flag, "planned-cut MUST NOT mask hard-refuse")
        assertEquals(EDRuleVerdict.HardRefuse, flag.severity)
    }

    @Test
    fun `RC14 planned-cut active suppresses soft-warn (kcal 1700)`() {
        val flag = EDDetectorHook.shouldShowCheckIn(
            EDState(
                kcalTarget = 1700,
                daysBelow80pct = 4,
                plannedCutActive = true,
            ),
        )
        assertNull(flag, "planned-cut should mask soft-warn")
    }

    @Test
    fun `weight rate above 0_9 kg per week trips HardRefuse (bigorexia-symmetric)`() {
        val flag = EDDetectorHook.shouldShowCheckIn(
            EDState(
                kcalTarget = 2750,
                weightRateKgPerWeek = 1.1,
                plannedCutActive = false,
            ),
        )
        assertNotNull(flag)
        assertEquals(EDRuleVerdict.HardRefuse, flag.severity)
        assertTrue(flag is EDFlag.WeightRateExcessive)
    }

    @Test
    fun `modal dismiss + planned-cut toggle callbacks fire`() {
        val flag = EDFlag.WeightRateExcessive(
            kgPerWeek = 0.6,
            severity = EDRuleVerdict.SoftWarn,
        )
        var planned by mutableStateOf(false)
        var dismissCalls = 0
        composeRule.setContent {
            DieticianLocaleProvider(locale = AppLocale.EN) {
                DieticianTheme {
                    EDSafeguardModal(
                        flag = flag,
                        plannedCutActive = planned,
                        onAdjustTarget = {},
                        onPlannedCutToggle = { planned = it },
                        onPauseTracking = {},
                        onDismiss = { dismissCalls += 1 },
                    )
                }
            }
        }
        composeRule.onNodeWithTag("ed-rules-soft-warn-banner").assertIsDisplayed()
        composeRule.onNodeWithTag("ed-checkin-planned-cut-toggle").performClick()
        assertEquals(true, planned)

        composeRule.onNodeWithTag("ed-checkin-dismiss").performClick()
        assertEquals(1, dismissCalls)
    }
}
