package com.sysnote8.musicord.command

import com.sysnote8.musicord.bot.Musicord
import com.sysnote8.musicord.extension.isHttpUrl
import com.sysnote8.musicord.extension.options
import com.sysnote8.musicord.extension.setup
import com.sysnote8.musicord.extension.toHmsString
import com.sysnote8.musicord.loop.LoopType
import com.sysnote8.musicord.player.GuildPlayer
import com.sysnote8.musicord.progress.createProgressBar
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.integer
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.message.embed
import kotlin.math.ceil

class MusicCommands(
    val bot: Musicord,
) {
    fun init() {
        registerPlay()
        registerPause()
        registerLeave()
        registerNowPlaying()
        registerShuffle()
        registerLoop()
        registerQueue()
        registerSkip()
        registerClear()
    }

    fun registerPlay() {
        Commands.register(
            "play",
            "曲を再生します",
            aliases = listOf("p"),
            args = {
                string("query", "The query you want to play")
            },
        ) {
            val (ack, userVoiceChId) = setup(queue, ignoreBotUnjoined = true)
            if (userVoiceChId == null) return@register

            // validate and create query
            val rawQuery: String = options["query"]?.value?.toString() ?: return@register
            val query =
                if (rawQuery.isHttpUrl()) {
                    rawQuery
                } else {
                    "ytsearch:$rawQuery"
                }

            val msg =
                ack.respond {
                    content = "読み込み中..."
                }

            // send query
            val playResult = queue.play(query, userVoiceChId)

            if (playResult.isSuccess) {
                msg.edit {
                    content = playResult.getOrNull()
                }
            } else {
                msg.edit {
                    content = "曲の再生に失敗しました。 理由: ${playResult.exceptionOrNull()!!.message}"
                }
            }
        }
    }

    fun registerPause() {
        Commands.register(
            "pause",
            "再生を一時停止(または再開)します",
        ) {
            val (ack, userVoiceChId) = setup(queue)
            if (userVoiceChId == null) return@register

            val isPlayerPaused = queue.togglePause()
            ack.respond {
                content =
                    if (!isPlayerPaused) {
                        "曲の再生を再開します。"
                    } else {
                        "曲の再生を一時停止しました。"
                    }
            }
        }
    }

    fun registerLeave() {
        Commands.register(
            "leave",
            "ボイスチャンネルから切断します",
            aliases = listOf("disconnect", "dc", "kick", "stop", "quit"),
        ) {
            val (ack, userVoiceChId) = setup(queue)
            if (userVoiceChId == null) return@register

            queue.leave()
            bot.queue.remove(interaction.guildId)
            ack.respond {
                content = "切断しました。"
            }
        }
    }

    fun registerNowPlaying() {
        Commands.register(
            "nowplaying",
            "現在再生中の曲の情報を表示します",
            aliases = listOf("np"),
        ) {
            val (ack, userVoiceChId) = setup(queue)
            if (userVoiceChId == null) return@register

            val nowTrack = queue.currentTrack
            if (nowTrack == null) {
                ack.respond {
                    content = "何も再生していません。"
                }
            } else {
                val position = queue.currentPosition / 1000
                val length = nowTrack.info.length / 1000
                val nextTrack = queue.getNextTrack()
                ack.respond {
                    embed {
                        title = "再生中の曲"
                        description = "```${nowTrack.info.title}```"
                        field {
                            name = "再生時間"
                            value = "${createProgressBar(position, length)}\n${position.toHmsString()} / ${length.toHmsString()}"
                        }
                        field {
                            name = "アップロード者"
                            value = nowTrack.info.author
                        }
                        if (nextTrack != null) {
                            field {
                                name = "次の曲"
                                value = nextTrack.info.title
                            }
                        }
                    }
                }
            }
        }
    }

    fun registerShuffle() {
        Commands.register(
            "shuffle",
            "キューをシャッフルします",
        ) {
            val (ack, userVoiceChId) = setup(queue)
            if (userVoiceChId == null) return@register

            queue.shuffle()
            ack.respond {
                content = "キューをシャッフルしました。"
            }
        }
    }

    fun registerLoop() {
        Commands.register(
            "loop",
            "ループの設定をします",
            args = {
                string("type", "ループの種類") {
                    choice("off", "off")
                    choice("queue", "queue")
                }
            },
        ) {
            val (ack, userVoiceChId) = setup(queue)
            if (userVoiceChId == null) return@register

            val rawType = options["type"]?.value.toString()
            if (rawType.isEmpty()) return@register

            val type =
                try {
                    LoopType.valueOf(rawType.uppercase())
                } catch (_: IllegalArgumentException) {
                    ack.respond {
                        content = "ループの種類が不正です。"
                    }
                    return@register
                }

            queue.loopType = type
            ack.respond {
                content = "ループの設定を" +
                    when (queue.loopType) {
                        LoopType.OFF -> "無効"
                        LoopType.QUEUE -> "キュー全体"
                    } + "にしました。"
            }
        }
    }

    fun registerQueue() {
        Commands.register(
            "queue",
            "キューを表示します",
            aliases = listOf("q"),
            args = {
                integer("page", "ページ番号") {
                    required = false
                    minValue = 0
                }
            },
        ) {
            val (ack, userVoiceChId) = setup(queue)
            if (userVoiceChId == null) return@register
            val msg =
                ack.respond {
                    content = "取得中..."
                }

            val rawPage = options["page"]?.value
            val page = rawPage?.toString()?.toInt() ?: 1
            if (page < 1) {
                ack.respond {
                    content = "ページ数が範囲外です。"
                }
                return@register
            }

            if (page > ceil(queue.size().toDouble().div(QUEUE_ITEMS_PER_PAGE))) {
                ack.respond {
                    content = "ページ数が範囲外です。"
                }
                return@register
            }

            val pageStart = QUEUE_ITEMS_PER_PAGE * (page - 1)
            val items =
                queue
                    .getQueueItems(
                        page - 1,
                        QUEUE_ITEMS_PER_PAGE,
                    ).mapIndexed { i, t -> "${pageStart + i + 1}: ${t.info.title}" }
                    .joinToString("\n")
            msg.edit {
                content = ""
                embed {
                    title = "キューリスト ($page ページ目)"
                    description = items
                }
            }
        }
    }

    fun registerSkip() {
        Commands.register(
            "skip",
            "現在の曲をスキップします",
        ) {
            val (ack, userVoiceChId) = setup(queue)
            if (userVoiceChId == null) return@register

            queue.playNext()
            ack.respond {
                content = "現在再生中の曲をスキップしました。"
            }
        }
    }

    fun registerClear() {
        Commands.register(
            "clear",
            "キューを空にします",
        ) {
            val (ack, userVoiceChId) = setup(queue)
            if (userVoiceChId == null) return@register

            queue.clear()
            ack.respond {
                content = "キューを空にしました。"
            }
        }
    }

    companion object {
        const val QUEUE_ITEMS_PER_PAGE: Int = 10
    }

    private val GuildChatInputCommandInteractionCreateEvent.queue: GuildPlayer
        get() {
            return bot.queue[interaction.guildId]
        }
}
