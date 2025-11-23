package com.sysnote8.musicord.command

import com.sysnote8.musicord.bot.Musicord
import com.sysnote8.musicord.extension.toMention
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.ChatInputCreateBuilder
import dev.kord.rest.builder.message.embed

data class CommandData(
    val rootCmdName: String,
    val cmdName: String,
    val description: String,
    val args: ChatInputCreateBuilder.() -> Unit,
    val handler: suspend GuildChatInputCommandInteractionCreateEvent.() -> Unit,
) {
    fun isRootCmd() = rootCmdName == cmdName
}

object Commands {
    var initialized: Boolean = false
        private set

    private val helpCmdMap: MutableMap<String, String> = mutableMapOf()

    private val cmdMap: MutableMap<String, CommandData> = mutableMapOf()

    suspend fun init(bot: Musicord) {
        MusicCommands(bot).init()
        registerHelp()
        // register
        println("Registering slash commands")
        bot.kord
            .createGuildApplicationCommands(bot.guildId, {
                cmdMap.forEach { (cmdName, v) ->
                    println("- $cmdName")
                    input(cmdName, v.description) {
                        v.args(this)
                    }
                }
            })
            .collect { cmd ->
                val cmdData = cmdMap[cmd.data.name] ?: return@collect
                if (cmdData.isRootCmd()) {
                    helpCmdMap[cmd.data.toMention()] = cmdData.description
                }
            }
        initialized = true
    }

    suspend fun onCommand(event: GuildChatInputCommandInteractionCreateEvent) {
        val cmd = cmdMap[event.interaction.command.rootName] ?: return
        cmd.handler(event)
    }

    fun register(
        cmdName: String,
        description: String,
        args: ChatInputCreateBuilder.() -> Unit = {},
        aliases: List<String> = listOf(),
        handler: suspend GuildChatInputCommandInteractionCreateEvent.() -> Unit,
    ) {
        if (cmdMap.containsKey(cmdName)) {
            throw RuntimeException("This command name already registered -> $cmdName")
        }

        aliases.toMutableList().apply { add(cmdName) }.forEach { name ->
            val data =
                CommandData(
                    cmdName,
                    name,
                    description,
                    args,
                    handler,
                )
            cmdMap[name] = data
        }
    }

    fun registerHelp() {
        register(
            "help",
            "ヘルプを表示します。",
        ) {
            val ack = interaction.deferPublicResponse()
            ack.respond {
                embed {
                    title = "コマンド一覧"
                    field {
                        name = "一般"
                        value = helpCmdMap.map { (k, v) -> "$k: $v" }.joinToString("\n")
                    }
                }
            }
        }
    }
}
