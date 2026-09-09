package com.ipterebi.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * What the panel says about the line, in a form fit to put on screen.
 *
 * Here rather than in the UI because none of it needs Android and all of it is
 * easy to get subtly wrong — `exp_date` is unix seconds in a string, absent for
 * a line that does not expire, and occasionally zero, which is not 1970.
 */
fun UserInfo.expiryLabel(zone: ZoneId = ZoneId.systemDefault()): String {
    val seconds = expiryEpochSeconds.toLongOrNull()
    if (expiryEpochSeconds.isBlank() || seconds == null || seconds <= 0L) return "No expiry"
    return Instant.ofEpochSecond(seconds)
        .atZone(zone)
        .format(DateTimeFormatter.ofPattern("d MMM yyyy"))
}

/** e.g. "1 of 2 connections in use". Blank when the panel does not say. */
fun UserInfo.connectionsLabel(): String {
    val max = maxConnections.takeIf { it.isNotBlank() } ?: return ""
    val active = activeConnections.takeIf { it.isNotBlank() } ?: "?"
    return "$active of $max connections in use"
}
