package com.sysnote8.musicord.extension

import dev.kord.core.cache.data.ApplicationCommandData

// </NAME:COMMAND_ID>
fun ApplicationCommandData.toMention() = "</$name:$id>"
