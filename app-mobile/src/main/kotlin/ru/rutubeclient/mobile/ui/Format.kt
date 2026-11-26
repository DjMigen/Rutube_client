package ru.rutubeclient.mobile.ui

import java.util.Locale

/** 12_345_678 -> "12 млн", 45_300 -> "45 тыс." */
fun formatCount(value: Long): String = when {
    value >= 1_000_000 -> String.format(Locale("ru"), "%.1f млн", value / 1_000_000.0)
        .replace(".0 ", " ")
    value >= 1_000 -> String.format(Locale("ru"), "%.1f тыс.", value / 1_000.0)
        .replace(".0 ", " ")
    else -> value.toString()
}

fun formatViews(hits: Long): String = "${formatCount(hits)} просмотров"

/** Seconds -> "1:23:45" or "12:34". */
fun formatDuration(totalSeconds: Int): String {
    if (totalSeconds <= 0) return ""
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}

fun formatMs(ms: Long): String = formatDuration((ms / 1000).toInt())

/** "2026-11-20T14:00:16" -> "20.11.2026" */
fun formatDate(iso: String?): String {
    if (iso == null || iso.length < 10) return ""
    return runCatching {
        val y = iso.substring(0, 4)
        val m = iso.substring(5, 7)
        val d = iso.substring(8, 10)
        "$d.$m.$y"
    }.getOrDefault("")
}

/** Bytes -> "1,4 ГБ" */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale("ru"), "%.1f ГБ", bytes / 1073741824.0)
    bytes >= 1L shl 20 -> String.format(Locale("ru"), "%.1f МБ", bytes / 1048576.0)
    bytes >= 1L shl 10 -> String.format(Locale("ru"), "%.1f КБ", bytes / 1024.0)
    else -> "$bytes Б"
}
