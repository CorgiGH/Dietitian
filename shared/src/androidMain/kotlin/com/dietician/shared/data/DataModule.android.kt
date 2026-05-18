package com.dietician.shared.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dietician.shared.data.local.WalPragmas
import com.dietician.shared.data.sql.DieticianDatabase
import com.dietician.shared.data.sync.WalCheckpointHook

object DataModuleAndroid {
    fun build(ctx: Context): DieticianDatabase {
        bindAndroidContext(ctx)
        val driver =
            AndroidSqliteDriver(
                schema = DieticianDatabase.Schema,
                context = ctx,
                name = "dietician.db",
                callback =
                object : AndroidSqliteDriver.Callback(DieticianDatabase.Schema) {
                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onOpen(db)
                        WalPragmas.INIT.forEach { db.execSQL(it) }
                    }
                },
            )
        // Council BREAK #5 mandate: checkpoint on app-background.
        WalCheckpointHook().registerOnBackground {
            WalPragmas.forceTruncatingCheckpoint(driver)
        }
        return DieticianDatabase(driver)
    }
}
