package com.sysnote8.musicord.extension

val HTTP_REGEX = "^https?://.+".toRegex()

fun String.isHttpUrl(): Boolean = HTTP_REGEX.matches(this)
