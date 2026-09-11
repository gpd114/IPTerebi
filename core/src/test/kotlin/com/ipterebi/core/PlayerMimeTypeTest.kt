package com.ipterebi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What another player is told a stream is, which decides which apps Android offers. */
class PlayerMimeTypeTest {

    @Test
    fun `live formats`() {
        assertEquals("video/mp2t", playerMimeType(StreamFormat.TS.extension))
        assertEquals("application/x-mpegurl", playerMimeType(StreamFormat.HLS.extension))
    }

    @Test
    fun `the containers films turn up in`() {
        assertEquals("video/mp4", playerMimeType("mp4"))
        assertEquals("video/x-matroska", playerMimeType("mkv"))
        assertEquals("video/x-msvideo", playerMimeType("avi"))
    }

    @Test
    fun `panels are not consistent about case or a leading dot`() {
        assertEquals("video/x-matroska", playerMimeType("MKV"))
        assertEquals("video/mp4", playerMimeType(".mp4"))
    }

    @Test
    fun `anything unknown is still video, so a player is offered rather than a browser`() {
        for (odd in listOf("", "xyz", "divx")) {
            assertTrue(playerMimeType(odd).startsWith("video/"), "\"$odd\" gave ${playerMimeType(odd)}")
        }
    }
}
