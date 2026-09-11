package com.ipterebi.app.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Handing a stream to another video player on the phone — VLC, MX Player, Just
 * Player — through Android's own chooser.
 *
 * The URL carries the line's username and password in its path, as every
 * stream URL does, and there is no way to give a player the stream without it.
 * So it goes only by `ACTION_VIEW` to an app the user picks, which is what
 * every IPTV app's "open in another player" does: never `ACTION_SEND`, which
 * would offer it to messaging apps, and never to the log.
 *
 * The extras are the ones the common players read. `title` is shown by VLC,
 * MX Player and Just Player instead of the URL — which, again, holds the
 * password. `headers` is MX Player's way to set the user agent, which matters
 * on panels that refuse unknown clients; VLC sends its own, which is the
 * default here anyway. `position` is VLC's resume point, in milliseconds.
 */
fun anotherPlayerIntent(
    url: String,
    mimeType: String,
    title: String?,
    userAgent: String,
    positionMs: Long,
): Intent = Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(Uri.parse(url), mimeType)
    title?.let { putExtra("title", it) }
    putExtra("headers", arrayOf("User-Agent", userAgent))
    if (positionMs > 0) putExtra("position", positionMs)
}

/**
 * Whether anything installed would take [intent]. Asked before stopping here:
 * with no other player, stopping would lose the stream for nothing. Needs the
 * `<queries>` in the manifest — without it, Android 11 and later hide every
 * other app and the answer is always no.
 */
@Suppress("DEPRECATION") // The flags overload is API 33; this app starts at 26.
fun Context.canOpenInAnotherPlayer(intent: Intent): Boolean =
    packageManager.queryIntentActivities(intent, 0).isNotEmpty()

/**
 * A chooser every time rather than a remembered default: which player copes
 * with a given panel varies, and a default that fails is hard to undo from
 * inside the app.
 */
fun Context.openInAnotherPlayer(intent: Intent) {
    startActivity(Intent.createChooser(intent, "Play with"))
}
