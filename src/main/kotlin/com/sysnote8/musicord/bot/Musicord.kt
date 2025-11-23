package com.sysnote8.musicord.bot

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.source.decodeFromStream
import com.sysnote8.musicord.command.Commands
import com.sysnote8.musicord.config.ConfigData
import com.sysnote8.musicord.queue.MusicQueue
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.event.user.VoiceStateUpdateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.schlaubi.lavakord.LavaKord
import dev.schlaubi.lavakord.kord.lavakord
import dev.schlaubi.lavakord.plugins.lavasrc.LavaSrc
import kotlinx.serialization.encodeToString
import java.io.File
import kotlin.system.exitProcess

class Musicord {
    lateinit var config: ConfigData
        private set
    lateinit var kord: Kord
        private set
    lateinit var lavalink: LavaKord
        private set
    lateinit var queue: MusicQueue
        private set
    var guildId: Snowflake = Snowflake(-1)

    fun loadConfig() {
        val configFile = File("config.toml")
        if (!configFile.exists()) {
            configFile.writeText(Toml.encodeToString(ConfigData()))
            println("Configを変更して再起動してください。")
            exitProcess(1)
        }

        config = Toml.decodeFromStream<ConfigData>(configFile.inputStream())
        guildId = Snowflake(config.discord.guildId)
    }

    @OptIn(PrivilegedIntent::class)
    suspend fun main() {
        loadConfig()

        // setup instances
        kord = Kord(config.discord.token)
        lavalink =
            kord.lavakord {
                plugins {
                    install(LavaSrc)
                }
                link {
                    resumeTimeout = 0
                }
            }
        lavalink.addNode(config.lavalink.host, config.lavalink.password)
        queue = MusicQueue(lavalink)

        // init commands
        Commands.init(this)

        // listen events
        kord.on<VoiceStateUpdateEvent> {
            // on leave
            if (this.state.channelId == null) {
                if (this.state.userId == kord.selfId) {
                    // is bot
                    queue.remove(this.state.guildId)
                } else {
                    // is non-bot
                    val oldState = this.old ?: return@on
                    queue[oldState.guildId].onUserLeaveChannel(oldState, kord.selfId) {
                        queue.remove(oldState.guildId)
                    }
                }
            }
        }

        kord.on<GuildChatInputCommandInteractionCreateEvent> {
            Commands.onCommand(this)
        }

        // login
        kord.login {
            intents += Intent.MessageContent + Intent.GuildMessages + Intent.GuildVoiceStates
        }
    }
}
