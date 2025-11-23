package com.sysnote8.musicord.queue

import com.sysnote8.musicord.player.GuildPlayer
import dev.kord.common.entity.Snowflake
import dev.schlaubi.lavakord.LavaKord
import dev.schlaubi.lavakord.kord.getLink

class MusicQueue(
    val lavalink: LavaKord,
) {
    private val queueMap: MutableMap<Snowflake, GuildPlayer> = mutableMapOf()

    operator fun get(id: Snowflake): GuildPlayer = queueMap.getOrPut(id, { GuildPlayer(lavalink.getLink(id)) })

    fun remove(id: Snowflake): GuildPlayer? = queueMap.remove(id)

    val size: Int = queueMap.size
}
