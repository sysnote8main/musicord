package com.sysnote8.musicord.config

import kotlinx.serialization.Serializable

@Serializable
data class ConfigData(
    val discord: DiscordConfig = DiscordConfig(),
    val lavalink: LavalinkConfig = LavalinkConfig(),
)

@Serializable
data class DiscordConfig(
    val token: String = "token is here",
    val guildId: ULong = (1234567890).toULong(),
)

@Serializable
data class LavalinkConfig(
    val host: String = "ws://localhost:2333",
    val password: String = "youshallnotpass",
)
