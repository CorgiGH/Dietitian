package com.dietician.shared.data

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

@Volatile private var androidContextRef: Context? = null

fun bindAndroidContext(ctx: Context) { androidContextRef = ctx.applicationContext }

@SuppressLint("HardwareIds")
actual fun deviceId(): String {
    val ctx = androidContextRef ?: error("bindAndroidContext must be called from Application.onCreate before deviceId()")
    val sec = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    return "android-$sec"
}
