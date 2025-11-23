package com.sysnote8.musicord.extension

import com.sysnote8.musicord.player.GuildPlayer
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.VoiceState
import dev.kord.core.entity.interaction.OptionValue
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent

val GuildChatInputCommandInteractionCreateEvent.options: Map<String, OptionValue<*>>
    get() = interaction.command.options

suspend fun GuildChatInputCommandInteractionCreateEvent.getVoiceState(): VoiceState = interaction.getMember().getVoiceState()

suspend fun GuildChatInputCommandInteractionCreateEvent.setup(
    queue: GuildPlayer,
    ignoreBotUnjoined: Boolean = false,
): Pair<DeferredPublicMessageInteractionResponseBehavior, Snowflake?> {
    // defer response
    val ack = interaction.deferPublicResponse()

    // get user voice channel id
    val userVoiceChId = getVoiceState().channelId
    if (userVoiceChId == null) {
        ack.respond {
            content = "ボットを操作する前にVCに接続してください!"
        }
        return Pair(ack, null)
    }
    if (!ignoreBotUnjoined) {
        if (!queue.isInChannel()) {
            ack.respond {
                content = "現在、ボットはどのVCにも参加していません。"
            }
            return Pair(ack, null)
        }
    }
    if (!queue.isPlayingChannel(userVoiceChId)) {
        ack.respond {
            content = "ボットの接続しているVCに接続していないようです。"
        }
        return Pair(ack, null)
    }
    return Pair(ack, userVoiceChId)
}
