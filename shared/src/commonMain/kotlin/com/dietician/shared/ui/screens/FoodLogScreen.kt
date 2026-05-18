package com.dietician.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.components.BarcodeScanButton
import com.dietician.shared.ui.components.ManualEntryField
import com.dietician.shared.ui.components.PhotoCaptureButton
import com.dietician.shared.ui.components.SameAsRecentButton
import com.dietician.shared.ui.components.VoiceRecordButton
import com.dietician.shared.ui.i18n.strings

/**
 * Multi-modal food logging surface — 5 stacked buttons (~80dp each) + a toast row.
 *
 * Order (per Batch B brief):
 * 1. VoiceRecordButton (RC1 fallback — taps show coming-soon toast)
 * 2. BarcodeScanButton (expect/actual lands Batch E Task 23)
 * 3. PhotoCaptureButton (expect/actual lands Batch E; PhotoSuggestionCard / RC11
 *    None-of-these escape hatch lands Batch D Task 19)
 * 4. SameAsRecentButton (BottomSheet of last 10 meals + tap-to-clone)
 * 5. ManualEntryField (autocomplete from Plan-1 local food DB)
 *
 * RC9: if coach is disabled (`SubjectStore.llm_coach_disabled = true` on the Plan-3
 * profile), surfaces that suggest AI-driven foods show the disabled-notice. FoodLog
 * itself doesn't AI-suggest, but the photo + same-as-AI-ranking paths consult it.
 *
 * testTag selectors per Batch B brief: foodlog-voice-button, foodlog-barcode-button,
 * foodlog-photo-button, foodlog-same-as-button, foodlog-manual-field,
 * foodlog-voice-coming-soon-toast.
 */
@Composable
fun FoodLogScreen(
    viewModel: FoodLogViewModel,
    onBarcodeScan: () -> Unit = {},
    onPhotoCapture: () -> Unit = {},
    onSameAsRecent: () -> Unit = { viewModel.showSameAsSheet() },
) {
    val state by viewModel.state.collectAsState()
    val s = strings()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(rememberScrollState())
            .testTag("foodlog-screen"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VoiceRecordButton(onTap = viewModel::onVoiceTap)
        if (state.voiceToastVisible) {
            Card(modifier = Modifier.padding(8.dp).testTag("foodlog-voice-coming-soon-toast")) {
                Text(text = s.voice_coming_soon, modifier = Modifier.padding(12.dp))
            }
        }
        BarcodeScanButton(onTap = onBarcodeScan)
        PhotoCaptureButton(onTap = onPhotoCapture)
        if (state.coachDisabled) {
            // RC9 — surfaces that would AI-suggest show the notice when coach disabled.
            Card(modifier = Modifier.padding(8.dp).testTag("foodlog-coach-disabled-notice")) {
                Text(text = s.coach_disabled_notice, modifier = Modifier.padding(12.dp))
            }
        }
        SameAsRecentButton(onTap = onSameAsRecent)
        ManualEntryField(
            query = state.manualQuery,
            onQueryChange = viewModel::onManualQueryChange,
        )
    }
}
