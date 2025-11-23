package com.sysnote8.musicord.player

import com.sysnote8.musicord.loop.LoopType
import dev.arbjerg.lavalink.protocol.v4.LoadResult
import dev.arbjerg.lavalink.protocol.v4.Track
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.VoiceState
import dev.schlaubi.lavakord.audio.Link
import dev.schlaubi.lavakord.audio.PlayerUpdateEvent
import dev.schlaubi.lavakord.audio.TrackEndEvent
import dev.schlaubi.lavakord.audio.TrackStartEvent
import dev.schlaubi.lavakord.audio.on
import dev.schlaubi.lavakord.audio.player.Player
import dev.schlaubi.lavakord.rest.loadItem
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.filter
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min

class GuildPlayer(
    val link: Link,
) {
    var loopType: LoopType = LoopType.OFF
    var currentPosition: Long = -1
        private set
    var currentTrack: Track? = null
        private set
    private var currentVoiceChannelId: Snowflake? = null
    private var destroyed: Boolean = false
    private val player: Player =
        link.player.also {
            it.on<TrackEndEvent> event@{
                onTrackEnd(this)
            }
            it.on<TrackStartEvent> event@{
                currentTrack = this.track
            }
            it.on<PlayerUpdateEvent> event@{
                currentPosition = this.state.position
            }
        }
    private val queueLock = ReentrantLock()
    private val queue: MutableList<Track> = mutableListOf()

    fun isInChannel(): Boolean = currentVoiceChannelId != null

    fun isPlayingChannel(userChId: Snowflake): Boolean = !isInChannel() || currentVoiceChannelId == userChId

    suspend fun onUserLeaveChannel(
        userOldVoiceState: VoiceState,
        botUserId: Snowflake,
        onLeaveHandler: () -> Unit,
    ) {
        val userVoiceChId = userOldVoiceState.channelId ?: return
        if (currentVoiceChannelId == null || !isPlayingChannel(userVoiceChId)) return
        val vcCh = userOldVoiceState.getChannelOrNull() ?: return
        val vcUserCount = vcCh.voiceStates.filter { it.userId != botUserId }.count()
        if (vcUserCount == 0) {
            leave()
            onLeaveHandler()
        }
    }

    suspend fun play(
        query: String,
        voiceChannelId: Snowflake,
    ): Result<String> {
        if (destroyed) return Result.failure(RuntimeException("無効なプレイヤーが使用されています。"))
        when (val item = link.loadItem(query)) {
            is LoadResult.TrackLoaded -> {
                playOrAddQueue(item.data, voiceChannelId)
                return Result.success("`${item.data.info.title}` がキューに追加されました。")
            }
            is LoadResult.PlaylistLoaded -> {
                item.data.tracks.forEach { t -> playOrAddQueue(t, voiceChannelId) }
                return Result.success("${item.data.tracks.size} 曲がキューに追加されました。")
            }
            is LoadResult.SearchResult -> {
                val track = item.data.tracks.first()
                playOrAddQueue(track, voiceChannelId)
                return Result.success("`${track.info.title}` がキューに追加されました。")
            }
            is LoadResult.NoMatches -> {
                return Result.failure(PlayerException("曲が見つかりませんでした。"))
            }
            is LoadResult.LoadFailed -> {
                return Result.failure(PlayerException(item.data.message ?: "不明なエラーが発生しました。"))
            }
        }
    }

    /**
     * @return is player paused
     */
    suspend fun togglePause(): Boolean {
        player.pause(!player.paused)
        return player.paused
    }

    suspend fun pause() {
        player.pause(true)
    }

    suspend fun resume() {
        player.pause(false)
    }

    fun size() = queueLock.withLock { queue.size }

    fun getNextTrack(): Track? = queueLock.withLock { queue.firstOrNull() }

    fun getQueueItems(
        page: Int,
        items: Int = 10,
    ): List<Track> {
        val start = page * items
        val end = min((page + 1) * items, queue.size)
        return queueLock.withLock { queue.subList(start, end).toList() }
    }

    suspend fun leave() {
        link.destroy()
        destroyed = true
        currentVoiceChannelId = null
    }

    fun shuffle() {
        queueLock.withLock {
            queue.shuffle()
        }
    }

    fun clear() {
        queueLock.withLock {
            queue.clear()
        }
    }

    suspend fun playOrAddQueue(
        track: Track,
        voiceChannelId: Snowflake,
    ) {
        if (queueLock.withLock { queue.isEmpty() } && player.playingTrack == null) {
            if (link.state != Link.State.CONNECTED) {
                link.connectAudio(voiceChannelId.value)
                currentVoiceChannelId = voiceChannelId
            } else {
                // Todo: handle this
            }
            player.playTrack(track = track)
        } else {
            queueLock.withLock {
                queue.add(track)
            }
        }
    }

    suspend fun playNext() {
        val result =
            queueLock.withLock {
                if (!queue.isEmpty()) {
                    queue.removeFirst()
                } else {
                    return
                }
            }
        player.playTrack(track = result)
    }

    private suspend fun onTrackEnd(e: TrackEndEvent) {
        if (e.guildId != e.guildId) return

        if (loopType == LoopType.QUEUE) {
            queueLock.withLock {
                queue.add(e.track)
            }
        }

        if (e.reason.mayStartNext) {
            playNext()
        }
    }
}
