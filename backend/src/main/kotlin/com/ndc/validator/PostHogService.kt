package com.ndc.validator

import com.posthog.server.PostHog
import com.posthog.server.PostHogConfig
import com.posthog.server.PostHogInterface

class PostHogService {
    private val client: PostHogInterface?

    init {
        val apiKey = System.getenv("POSTHOG_API_KEY")
        client = if (!apiKey.isNullOrBlank()) {
            val config = PostHogConfig.builder(apiKey)
                .host("https://eu.i.posthog.com")
                .build()
            PostHog.with(config)
        } else {
            null
        }
    }

    fun capture(event: String, properties: Map<String, Any> = emptyMap()) {
        client?.let {
            val finalProperties = HashMap(properties)
            // Ensure anonymous tracking by setting process_person_profile to false
            finalProperties["\$process_person_profile"] = false
            
            it.capture("anonymous_mcp_user", event, finalProperties)
        }
    }

    fun shutdown() {
        client?.close()
    }
}
