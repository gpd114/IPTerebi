package com.ipterebi.core

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The guide is the part of the API most likely to be absent, malformed, or in
 * the wrong timezone, and the part where being wrong is most visible: a title
 * shown as base64, or a programme an hour out, is worse than an empty panel.
 */
class EpgTest {

    // 2024-12-31 20:00:00Z .. 21:00:00Z
    private val eightPm = 1735675200L
    private val ninePm = 1735678800L

    private fun listing(
        title: String = "TW9ybmluZyBOZXdz",
        start: Long = eightPm,
        stop: Long = ninePm,
    ) = EpgListing(title = title, startTimestamp = start, stopTimestamp = stop)

    @Test
    fun `a base64 title is decoded`() {
        assertEquals("Morning News", listing().titleText)
    }

    @Test
    fun `a plain text title a fork sent unencoded is left readable`() {
        // "News" is itself valid base64 and decodes to three bytes of noise, so
        // "did it decode" is not the question — whether the result is text is.
        assertEquals("News", listing(title = "News").titleText)
    }

    @Test
    fun `a plain title with spaces survives`() {
        assertEquals("The Ten O'Clock News", listing(title = "The Ten O'Clock News").titleText)
    }

    @Test
    fun `an empty title stays empty rather than becoming noise`() {
        assertEquals("", listing(title = "").titleText)
    }

    @Test
    fun `a base64 description wrapped across lines is decoded`() {
        // Panels wrap long base64 at 76 characters. The strict decoder rejects
        // the line breaks; the MIME decoder is used for exactly this reason.
        val text = "A long description that a panel would wrap when encoding it."
        val encoded = java.util.Base64.getMimeEncoder(20, "\n".toByteArray()).encodeToString(text.toByteArray())

        assertTrue(encoded.contains("\n"), "test payload was not wrapped: $encoded")
        assertEquals(text, EpgListing(description = encoded).descriptionText)
    }

    @Test
    fun `a title with an accent survives the round trip`() {
        val encoded = java.util.Base64.getEncoder().encodeToString("Journal télévisé".toByteArray())
        assertEquals("Journal télévisé", listing(title = encoded).titleText)
    }

    @Test
    fun `a programme is on between its start and its end`() {
        val programme = listing()

        assertTrue(programme.isOnAt(Instant.ofEpochSecond(eightPm)))
        assertTrue(programme.isOnAt(Instant.ofEpochSecond(eightPm + 1800)))
        // The end is exclusive, or the nine o'clock programme would also be on.
        assertFalse(programme.isOnAt(Instant.ofEpochSecond(ninePm)))
        assertFalse(programme.isOnAt(Instant.ofEpochSecond(eightPm - 1)))
    }

    @Test
    fun `a listing with no timestamps is never on, rather than being on in 1970`() {
        val broken = EpgListing(title = "Unknown", startTimestamp = 0, stopTimestamp = 0)

        assertFalse(broken.hasKnownTimes)
        assertFalse(broken.isOnAt(Instant.ofEpochSecond(eightPm)))
        assertNull(broken.progressAt(Instant.ofEpochSecond(eightPm)))
        assertEquals("", broken.timeLabel())
    }

    @Test
    fun `a listing that ends before it starts is not trusted`() {
        val backwards = EpgListing(startTimestamp = ninePm, stopTimestamp = eightPm)
        assertFalse(backwards.hasKnownTimes)
    }

    @Test
    fun `progress runs from zero to one across the programme`() {
        val programme = listing()

        assertEquals(0f, programme.progressAt(Instant.ofEpochSecond(eightPm)))
        assertEquals(0.5f, programme.progressAt(Instant.ofEpochSecond(eightPm + 1800)))
        // Clamped: a panel whose guide is behind should not push a bar past full.
        assertEquals(1f, programme.progressAt(Instant.ofEpochSecond(ninePm + 9999)))
        assertEquals(0f, programme.progressAt(Instant.ofEpochSecond(eightPm - 9999)))
    }

    @Test
    fun `the time label is rendered in the viewer's zone, not the panel's`() {
        // The `start` and `end` strings carry no timezone at all, which is why
        // nothing reads them. These are unix seconds, so the answer is defined.
        val programme = listing()

        assertEquals("20:00 – 21:00", programme.timeLabel(ZoneId.of("UTC")))
        assertEquals("21:00 – 22:00", programme.timeLabel(ZoneId.of("Europe/Paris")))
    }

    @Test
    fun `now and next are picked by the clock rather than by arrival order`() {
        val tenPm = ninePm + 3600
        // Deliberately out of order: arrival order is not promised.
        val listings = listOf(
            listing(title = "TmluZQ==", start = ninePm, stop = tenPm),
            listing(title = "RWlnaHQ=", start = eightPm, stop = ninePm),
        )

        val (now, next) = listings.nowAndNext(Instant.ofEpochSecond(eightPm + 60))

        assertEquals("Eight", now?.titleText)
        assertEquals("Nine", next?.titleText)
    }

    @Test
    fun `a gap in the schedule leaves nothing on now but something on next`() {
        val listings = listOf(listing(start = ninePm, stop = ninePm + 3600))

        val (now, next) = listings.nowAndNext(Instant.ofEpochSecond(eightPm))

        assertNull(now, "invented a programme to fill a gap")
        assertEquals(ninePm, next?.startTimestamp)
    }

    @Test
    fun `undated listings are ignored when choosing now and next`() {
        val listings = listOf(
            EpgListing(title = "no times at all"),
            listing(start = eightPm, stop = ninePm),
        )

        val (now, next) = listings.nowAndNext(Instant.ofEpochSecond(eightPm + 60))

        assertEquals(eightPm, now?.startTimestamp)
        assertNull(next)
    }

    @Test
    fun `an empty guide is not an error`() {
        val (now, next) = emptyList<EpgListing>().nowAndNext(Instant.now())

        assertNull(now)
        assertNull(next)
    }

    @Test
    fun `timestamps parse whether the panel quotes them or not`() {
        val quoted = """{"epg_listings":[{"start_timestamp":"1735675200","stop_timestamp":"1735678800"}]}"""
        val bare = """{"epg_listings":[{"start_timestamp":1735675200,"stop_timestamp":1735678800}]}"""

        val fromQuoted = LenientJson.decodeFromString(ShortEpgResponse.serializer(), quoted)
        val fromBare = LenientJson.decodeFromString(ShortEpgResponse.serializer(), bare)

        assertEquals(eightPm, fromQuoted.listings.single().startTimestamp)
        assertEquals(fromBare.listings.single(), fromQuoted.listings.single())
    }

    @Test
    fun `a timestamp beyond 2038 is not truncated`() {
        // Unix seconds stop fitting in an Int in January 2038, and a guide is
        // the one thing here routinely asked about the future.
        val far = 2_400_000_000L
        val body = """{"epg_listings":[{"start_timestamp":$far,"stop_timestamp":${far + 3600}}]}"""

        val parsed = LenientJson.decodeFromString(ShortEpgResponse.serializer(), body)

        assertEquals(far, parsed.listings.single().startTimestamp)
    }

    @Test
    fun `a guide response missing epg_listings entirely parses as empty`() {
        val parsed = LenientJson.decodeFromString(ShortEpgResponse.serializer(), "{}")
        assertTrue(parsed.listings.isEmpty())
    }

    @Test
    fun `a full listing from the documented shape parses end to end`() {
        val body = """
            {"epg_listings":[{
              "id":"12345","epg_id":"67890",
              "title":"TW9ybmluZyBOZXdz",
              "description":"RGFpbHkgbmV3cyBicm9hZGNhc3Q=",
              "start":"2024-12-31 20:00:00","end":"2024-12-31 21:00:00",
              "start_timestamp":$eightPm,"stop_timestamp":$ninePm,
              "now_playing":1,"has_archive":0
            }]}
        """.trimIndent()

        val programme = LenientJson.decodeFromString(ShortEpgResponse.serializer(), body).listings.single()

        assertEquals("Morning News", programme.titleText)
        assertEquals("Daily news broadcast", programme.descriptionText)
        assertEquals("12345", programme.id)
        assertTrue(programme.hasKnownTimes)
    }
}
