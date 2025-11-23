package com.sysnote8.musicord.progress

fun createProgressBar(
    progress: Long,
    total: Long,
    barLength: Int = 20,
): String {
    val ratio = progress.toDouble() / total
    val filled = (ratio * barLength).toInt()
    val empty = barLength - filled

    val bar = "█".repeat(filled) + "─".repeat(empty)
    val percent = (ratio * 100).toLong()

    return "[$bar] $percent%"
}
