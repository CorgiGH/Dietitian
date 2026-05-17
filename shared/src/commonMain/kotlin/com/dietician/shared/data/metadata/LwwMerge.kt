package com.dietician.shared.data.metadata

data class Lww<T>(val value: T, val hlc: HlcTimestamp, val serverRecvAt: Long?)

/**
 * Pure LWW pick using tuple (serverRecvAt-nulls-last, hlc.wallMs, hlc.seq, hlc.deviceId).
 * serverRecvAt is the server-stamped receive time; null = local-only (pre-sync) and
 * ranks BELOW any server-stamped value (the council "tie -> remote" rule).
 *
 * Plan-deviation note: plan body had `-(it.serverRecvAt ?: Long.MIN_VALUE)` which makes
 * null rank as the SMALLEST key (winning over any server-stamped value). The correct
 * mapping for nulls-last is: server-stamped values get key = -serverRecvAt (so the
 * largest serverRecvAt sorts smallest = winning); null gets key = Long.MAX_VALUE
 * (sorts last = losing).
 */
object LwwMerge {
    fun <T> pick(
        a: Lww<T>,
        b: Lww<T>,
    ): Lww<T> {
        val cmp =
            compareValuesBy(
                a,
                b,
                { it.serverRecvAt?.let { v -> -v } ?: Long.MAX_VALUE },
                { -it.hlc.wallMs },
                { -it.hlc.seq },
                { it.hlc.deviceId },
            )
        return if (cmp <= 0) a else b
    }
}
