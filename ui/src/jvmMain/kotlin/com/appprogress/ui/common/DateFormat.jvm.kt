package com.appprogress.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val formatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.forLanguageTag("ru"))

actual fun formatDateTime(epochMillis: Long): String =
    formatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
