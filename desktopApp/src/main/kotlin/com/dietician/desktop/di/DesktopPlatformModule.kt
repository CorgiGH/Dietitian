package com.dietician.desktop.di

import com.dietician.shared.data.DataModuleDesktop
import com.dietician.shared.data.sql.DieticianDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop Koin module.
 *
 * Provides the [DieticianDatabase] singleton via [DataModuleDesktop.build] which
 * opens the JDBC SQLite driver at `%APPDATA%/Dietician/dietician.db` (Win) or
 * `~/Dietician/dietician.db`, applies the SQLDelight schema on first launch,
 * and toggles WAL pragmas per Plan-1 RC5.
 */
val desktopPlatformModule: Module = module {
    single<DieticianDatabase> { DataModuleDesktop.build() }
}
