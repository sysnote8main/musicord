package com.sysnote8.musicord.extension

fun Long.toHmsString(): String {
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    val seconds = this % 60

    return buildString {
        if (hours > 0) append("${hours}h")
        if (minutes > 0) append("${minutes}m")
        append("${seconds}s")
    }
}
