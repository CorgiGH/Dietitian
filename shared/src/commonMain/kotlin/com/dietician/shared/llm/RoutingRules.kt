package com.dietician.shared.llm

/**
 * Resolve the failover chain for an [LlmRequest].
 *
 * Lookup order:
 *   1. Exact (deviceClass, task) hit in [RouterConfig.chains] — caller may override default.
 *   2. Closed `when` fallback per [TaskType] + [DeviceClass] that returns a councilbaked
 *      default chain.
 *
 * RC8 (Council 1779062699): the inner `when` is exhaustive on [TaskType] AND on
 * [DeviceClass]. Adding a new enum variant fails compilation here, forcing the dev to
 * declare a routing intent rather than silently falling through to a default.
 *
 * RC1 placeholder: [TaskType.VISION] currently routes to VICTOR_DESKTOP_TEXT because Batch
 * B (Tasks 10-12) lands vision-capable provider serialization. Once those ship, RoutingRules
 * gains a per-provider vision capability filter (capability registry).
 */
object RoutingRules {
    fun selectChain(config: RouterConfig, request: LlmRequest): List<LlmProvider> {
        val key = ChainKey(request.deviceClass, request.task)
        config.chains[key]?.let { return it }

        return when (request.task) {
            TaskType.TEXT -> when (request.deviceClass) {
                DeviceClass.VICTOR_DESKTOP -> DefaultRouterConfig.VICTOR_DESKTOP_TEXT
                DeviceClass.FRIEND_PHONE -> DefaultRouterConfig.FRIEND_PHONE_TEXT
                DeviceClass.SERVER -> DefaultRouterConfig.VICTOR_DESKTOP_TEXT
                DeviceClass.ANY -> DefaultRouterConfig.VICTOR_DESKTOP_TEXT
            }
            TaskType.MODERATION -> when (request.deviceClass) {
                DeviceClass.VICTOR_DESKTOP -> DefaultRouterConfig.VICTOR_DESKTOP_MODERATION
                DeviceClass.FRIEND_PHONE -> DefaultRouterConfig.FRIEND_MODERATION
                DeviceClass.SERVER -> DefaultRouterConfig.VICTOR_DESKTOP_MODERATION
                DeviceClass.ANY -> DefaultRouterConfig.VICTOR_DESKTOP_MODERATION
            }
            TaskType.VISION -> when (request.deviceClass) {
                DeviceClass.VICTOR_DESKTOP -> DefaultRouterConfig.VICTOR_DESKTOP_TEXT
                DeviceClass.FRIEND_PHONE -> DefaultRouterConfig.FRIEND_PHONE_TEXT
                DeviceClass.SERVER -> DefaultRouterConfig.VICTOR_DESKTOP_TEXT
                DeviceClass.ANY -> DefaultRouterConfig.VICTOR_DESKTOP_TEXT
            }
            TaskType.EMBEDDING -> DefaultRouterConfig.SERVER_EMBEDDING
        }
    }
}
