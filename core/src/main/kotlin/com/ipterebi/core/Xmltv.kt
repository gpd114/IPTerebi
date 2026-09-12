package com.ipterebi.core

import java.io.Reader
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The whole line's schedule, from `xmltv.php`.
 *
 * `get_short_epg` answers for one channel at a time, a few programmes ahead,
 * and on the first real line it answered nothing at all for many channels. A
 * guide grid needs every channel, days ahead, and `xmltv.php` is the only call
 * that gives it: the entire schedule as one XMLTV document, tens of megabytes on
 * a big line. So it is read as it downloads, one programme at a time, and handed
 * on — never held whole. A TV box has a phone's memory from years ago.
 *
 * Programmes are matched to channels by [XmltvProgramme.channel], which is a
 * channel's `epg_channel_id`, not its stream id — several streams (HD, SD, a
 * backup) often share one.
 */
data class XmltvChannel(val id: String, val name: String, val icon: String)

data class XmltvProgramme(
    /** The channel's `epg_channel_id`. */
    val channel: String,
    /** Unix seconds, from the XMLTV time and its own offset. */
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String,
)

/**
 * Reads an XMLTV document from [input], calling [onChannel] and [onProgramme]
 * as each one ends. A programme with no readable times, or no channel, is
 * skipped rather than failing the rest — a schedule of a hundred thousand
 * programmes should not be lost to one bad one. Anything else in the document
 * is ignored.
 *
 * Throws [XtreamException] when the document is not XMLTV at all — a panel that
 * does not implement `xmltv.php` tends to answer with its web page.
 */
fun readXmltv(
    input: Reader,
    onChannel: (XmltvChannel) -> Unit = {},
    onProgramme: (XmltvProgramme) -> Unit,
) {
    val xml = XmlPull(input)
    var sawRoot = false

    // The programme or channel being read, and which of its children we are in.
    var inProgramme = false
    var inChannel = false
    var channel = ""
    var start: Long? = null
    var stop: Long? = null
    var channelId = ""
    var icon = ""
    val title = StringBuilder()
    val desc = StringBuilder()
    val name = StringBuilder()
    var collecting: StringBuilder? = null
    var gotTitle = false
    var gotDesc = false
    var gotName = false

    while (true) {
        when (val event = xml.next() ?: break) {
            is XmlEvent.Start -> {
                if (!sawRoot) {
                    if (event.name != "tv") {
                        throw XtreamException(
                            "The panel sent a <${event.name}> page instead of a TV guide. It may not " +
                                "offer the full guide (xmltv.php), or it may be down."
                        )
                    }
                    sawRoot = true
                    continue
                }
                when {
                    event.name == "programme" -> {
                        inProgramme = true
                        channel = event.attributes["channel"].orEmpty().trim()
                        start = event.attributes["start"]?.let(::parseXmltvTime)
                        stop = event.attributes["stop"]?.let(::parseXmltvTime)
                        title.clear(); desc.clear(); gotTitle = false; gotDesc = false
                    }
                    event.name == "channel" -> {
                        inChannel = true
                        channelId = event.attributes["id"].orEmpty().trim()
                        icon = ""
                        name.clear(); gotName = false
                    }
                    // The first of each only: XMLTV repeats them per language,
                    // and one title is what a grid has room for.
                    inProgramme && event.name == "title" && !gotTitle -> collecting = title
                    inProgramme && event.name == "desc" && !gotDesc -> collecting = desc
                    inChannel && event.name == "display-name" && !gotName -> collecting = name
                    inChannel && event.name == "icon" -> icon = event.attributes["src"].orEmpty()
                }
            }

            is XmlEvent.Text -> collecting?.append(event.text)

            is XmlEvent.End -> when (event.name) {
                "title" -> if (collecting === title) { gotTitle = true; collecting = null }
                "desc" -> if (collecting === desc) { gotDesc = true; collecting = null }
                "display-name" -> if (collecting === name) { gotName = true; collecting = null }
                "programme" -> {
                    val from = start
                    val to = stop
                    if (inProgramme && channel.isNotEmpty() && from != null && to != null && to > from) {
                        onProgramme(
                            XmltvProgramme(channel, from, to, title.toString().trim(), desc.toString().trim())
                        )
                    }
                    inProgramme = false
                    collecting = null
                }
                "channel" -> {
                    if (inChannel && channelId.isNotEmpty()) {
                        onChannel(XmltvChannel(channelId, name.toString().trim(), icon))
                    }
                    inChannel = false
                    collecting = null
                }
            }
        }
    }
    if (!sawRoot) throw XtreamException("The panel sent an empty TV guide.")
}

/**
 * An XMLTV time — `20260912180000 +0000` — as unix seconds. Seconds may be
 * missing (`202609121800`), and so may the offset, in which case it is taken as
 * UTC: the standard leaves it to the reader, and a wrong guess is what
 * [GuideClock]'s kind of correction is for. Null for anything unreadable.
 */
fun parseXmltvTime(value: String): Long? {
    val text = value.trim()
    val digits = text.takeWhile { it.isDigit() }
    val local = when (digits.length) {
        14 -> digits
        12 -> digits + "00"
        else -> return null
    }
    val offsetText = text.substring(digits.length).trim()
    return try {
        val time = LocalDateTime.parse(local, XMLTV_LOCAL)
        val offset = if (offsetText.isEmpty()) ZoneOffset.UTC else ZoneOffset.of(normaliseOffset(offsetText) ?: return null)
        time.toEpochSecond(offset)
    } catch (e: Exception) {
        null
    }
}

/** `+0100` or `+01:00` or `-05` as `ZoneOffset.of` takes it. */
private fun normaliseOffset(text: String): String? {
    val sign = text.firstOrNull()?.takeIf { it == '+' || it == '-' } ?: return null
    val digits = text.drop(1).filter { it.isDigit() }
    return when (digits.length) {
        2 -> "$sign$digits:00"
        4 -> "$sign${digits.take(2)}:${digits.drop(2)}"
        else -> null
    }
}

private val XMLTV_LOCAL = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

internal sealed interface XmlEvent {
    data class Start(val name: String, val attributes: Map<String, String>) : XmlEvent
    data class End(val name: String) : XmlEvent
    data class Text(val text: String) : XmlEvent
}

/**
 * Just enough of an XML reader for XMLTV, pulling one event at a time from a
 * stream: elements and their attributes, text with its entities, CDATA; the
 * declaration, comments and a DOCTYPE skipped. Not a validating parser, and
 * forgiving on purpose — panels generate this by string concatenation.
 *
 * Hand-rolled because `core/` is plain JVM with no Android, where the XML
 * readers the two platforms share do not exist, and a library for one file
 * format this simple is weight for nothing.
 */
internal class XmlPull(private val input: Reader) {
    private val buffer = CharArray(64 * 1024)
    private var length = 0
    private var position = 0
    private val pendingEnds = ArrayDeque<String>()

    private fun read(): Int {
        if (position == length) {
            length = input.read(buffer, 0, buffer.size)
            position = 0
            if (length <= 0) {
                length = 0
                return -1
            }
        }
        return buffer[position++].code
    }

    private fun peek(): Int {
        val c = read()
        if (c >= 0) position--
        return c
    }

    fun next(): XmlEvent? {
        pendingEnds.removeFirstOrNull()?.let { return XmlEvent.End(it) }
        while (true) {
            val c = read()
            if (c < 0) return null
            if (c != '<'.code) {
                val text = StringBuilder()
                text.append(c.toChar())
                readTextInto(text)
                val decoded = decodeEntities(text)
                // Whitespace between elements says nothing.
                if (decoded.isNotBlank()) return XmlEvent.Text(decoded)
                continue
            }
            when (peek()) {
                '?'.code -> skipPast("?>")
                '!'.code -> {
                    read()
                    when {
                        startsWith("--") -> skipPast("-->")
                        startsWith("[CDATA[") -> return XmlEvent.Text(readUntil("]]>"))
                        else -> skipDeclaration()
                    }
                }
                '/'.code -> {
                    read()
                    val name = readName()
                    skipPast(">")
                    return XmlEvent.End(name)
                }
                else -> return readStart()
            }
        }
    }

    private fun readTextInto(out: StringBuilder) {
        while (true) {
            val c = peek()
            if (c < 0 || c == '<'.code) return
            out.append(read().toChar())
        }
    }

    private fun readStart(): XmlEvent {
        val name = readName()
        val attributes = HashMap<String, String>()
        while (true) {
            skipSpace()
            val c = read()
            when {
                c < 0 -> return XmlEvent.Start(name, attributes)
                c == '>'.code -> return XmlEvent.Start(name, attributes)
                c == '/'.code -> {
                    skipPast(">")
                    pendingEnds.addLast(name)
                    return XmlEvent.Start(name, attributes)
                }
                else -> {
                    val key = StringBuilder().append(c.toChar())
                    while (true) {
                        val k = peek()
                        if (k < 0 || k == '='.code || k == '>'.code || k == '/'.code || k.toChar().isWhitespace()) break
                        key.append(read().toChar())
                    }
                    skipSpace()
                    if (peek() != '='.code) {
                        attributes[key.toString()] = ""
                        continue
                    }
                    read()
                    skipSpace()
                    val quote = read()
                    val value = StringBuilder()
                    if (quote == '"'.code || quote == '\''.code) {
                        while (true) {
                            val v = read()
                            if (v < 0 || v == quote) break
                            value.append(v.toChar())
                        }
                    } else if (quote >= 0) {
                        // Unquoted, which is not XML — but it is what a panel
                        // assembling strings by hand sends.
                        value.append(quote.toChar())
                        while (true) {
                            val v = peek()
                            if (v < 0 || v == '>'.code || v.toChar().isWhitespace()) break
                            value.append(read().toChar())
                        }
                    }
                    attributes[key.toString()] = decodeEntities(value)
                }
            }
        }
    }

    private fun readName(): String {
        val name = StringBuilder()
        while (true) {
            val c = peek()
            if (c < 0 || c == '>'.code || c == '/'.code || c.toChar().isWhitespace()) break
            name.append(read().toChar())
        }
        return name.toString()
    }

    private fun skipSpace() {
        while (true) {
            val c = peek()
            if (c < 0 || !c.toChar().isWhitespace()) return
            read()
        }
    }

    /** Consumes [text] if it is next; nothing otherwise. Only called for short markers. */
    private fun startsWith(text: String): Boolean {
        // A partial match is consumed, not given back. That only happens in a
        // malformed declaration, which is skipped either way.
        for (ch in text) {
            if (peek() != ch.code) return false
            read()
        }
        return true
    }

    private fun skipPast(end: String) {
        readUntil(end)
    }

    /** Everything up to [end], consumed along with it. */
    private fun readUntil(end: String): String {
        val out = StringBuilder()
        while (true) {
            val c = read()
            if (c < 0) return out.toString()
            out.append(c.toChar())
            if (out.length >= end.length && out.endsWith(end)) {
                out.setLength(out.length - end.length)
                return out.toString()
            }
        }
    }

    /** A DOCTYPE and the like, which may carry a bracketed internal subset. */
    private fun skipDeclaration() {
        var depth = 0
        while (true) {
            val c = read()
            if (c < 0) return
            when (c) {
                '['.code -> depth++
                ']'.code -> depth--
                '>'.code -> if (depth <= 0) return
            }
        }
    }
}

/** `&amp;`, `&lt;`, `&#233;`, `&#xE9;` and the rest. An unknown entity is left as written. */
internal fun decodeEntities(text: CharSequence): String {
    if (text.indexOf('&') < 0) return text.toString()
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c != '&') {
            out.append(c)
            i++
            continue
        }
        val end = text.indexOf(';', i)
        if (end < 0 || end - i > 10) {
            out.append(c)
            i++
            continue
        }
        val entity = text.substring(i + 1, end)
        val decoded: String? = when {
            entity == "amp" -> "&"
            entity == "lt" -> "<"
            entity == "gt" -> ">"
            entity == "quot" -> "\""
            entity == "apos" -> "'"
            entity.startsWith("#x") || entity.startsWith("#X") ->
                entity.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
            entity.startsWith("#") -> entity.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
            else -> null
        }
        if (decoded == null) {
            out.append(c)
            i++
        } else {
            out.append(decoded)
            i = end + 1
        }
    }
    return out.toString()
}

/** A full-guide programme in the shape the screens read. Its times are already right. */
fun XmltvProgramme.asListing(): EpgListing = EpgListing(
    epgId = channel,
    title = title,
    description = description,
    startTimestamp = start,
    stopTimestamp = stop,
    plainText = true,
)
