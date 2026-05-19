package com.dietician.shared.llm

/**
 * iter-11 — locale enum routed into the server-side CoachSystemPrompts selector.
 * Romanian copy in the server uses comma-below ș/ț (U+0219 / U+021B).
 */
enum class CoachLocale {
    EN,
    RO,
    ;

    fun wire(): String = name.lowercase()
}
