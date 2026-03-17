package com.echoproduction.metroclock.models

data class WorkspaceConfig(
    val clickupApiToken: String? = null,
    val clickupUserMappings: Map<String, String> = emptyMap(),
    val slackWebhookUrl: String? = null,
    val discordWebhookUrl: String? = null,
    val slackUserMappings: Map<String, String> = emptyMap(),
    val discordUserMappings: Map<String, String> = emptyMap()
) {
    val hasClickUp: Boolean get() = clickupApiToken != null
}