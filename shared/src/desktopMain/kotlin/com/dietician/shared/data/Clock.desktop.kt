package com.dietician.shared.data

actual class WallClock {
    actual fun nowMillis(): Long = System.currentTimeMillis()
}
