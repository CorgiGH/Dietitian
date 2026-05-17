package com.dietician.shared.data.api

import kotlinx.serialization.Serializable

/** Pull cursor — strict (timestamp, eventUuid) half-open `>` semantics per council BREAK #3. */
@Serializable
data class Cursor(val timestampMs: Long, val eventUuid: String) : Comparable<Cursor> {
    override fun compareTo(other: Cursor): Int {
        val t = timestampMs.compareTo(other.timestampMs)
        return if (t != 0) t else eventUuid.compareTo(other.eventUuid)
    }

    companion object {
        val ZERO = Cursor(0L, "")
    }
}
