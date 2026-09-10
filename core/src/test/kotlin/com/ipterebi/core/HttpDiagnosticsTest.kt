package com.ipterebi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The sentences a stream refusal becomes, in place of a bare status code. */
class HttpDiagnosticsTest {

    @Test
    fun `the connection limit codes are named, for every kind of thing`() {
        // 458 is from a real line, refusing reconnects while it still counted
        // the connection that had just dropped.
        for (code in listOf(456, 458)) {
            for (message in listOf(
                describeStreamHttpError(code),
                describeFilmHttpError(code),
                describeEpisodeHttpError(code),
            )) {
                assertTrue(message.contains("connection limit"), "$code said: $message")
                assertFalse(message.contains("$code"), "$code reported as a number: $message")
            }
        }
    }

    @Test
    fun `a channel that is not there points at the stream format`() {
        assertTrue(describeStreamHttpError(404).contains("MPEG-TS and HLS"))
    }

    @Test
    fun `a server error says it is the provider's, with the code`() {
        val message = describeStreamHttpError(503)
        assertTrue(message.contains("provider"))
        assertTrue(message.contains("503"))
    }

    @Test
    fun `anything unknown still says which code it was`() {
        assertEquals("The stream could not be opened (HTTP 418).", describeStreamHttpError(418))
    }
}
