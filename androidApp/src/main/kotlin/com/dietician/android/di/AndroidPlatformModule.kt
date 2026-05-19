package com.dietician.android.di

import android.content.Context
import com.dietician.android.CameraXCapture
import com.dietician.android.MediaStoreSaver
import com.dietician.shared.data.DataModuleAndroid
import com.dietician.shared.data.sql.DieticianDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android Koin module.
 *
 * Bindings:
 *   - [Context] — the application context (parameterized factory; Koin gets the
 *     Application reference at start time and hands it off via the module DSL).
 *   - [CameraXCapture] — singleton; needs Context.
 *   - [MediaStoreSaver] — singleton; needs Context.
 *
 * The expect/actual platform glue ([com.dietician.shared.ui.data.captureImage],
 * [com.dietician.shared.ui.data.saveExportedFile],
 * [com.dietician.shared.ui.data.DieticianClipboardManager]) is wired via
 * `AndroidPlatformHandle` rather than going through Koin — those are top-level
 * functions / class actuals that don't have an injectable receiver. The
 * handle holds the Context reference so the actuals can resolve it lazily.
 */
fun androidPlatformModule(applicationContext: Context): Module = module {
    single<Context> { applicationContext }
    single { CameraXCapture(get()) }
    single { MediaStoreSaver(get()) }
    single<DieticianDatabase> { DataModuleAndroid.build(get()) }
}
