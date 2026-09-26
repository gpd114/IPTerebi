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

/**
 * What a failure to reach the panel at all is turned into. These are the
 * messages people see on the day their provider falls over and on the day they
 * mistype the address, and telling those two apart is the whole point.
 */
class NetworkFailureTest {

    @Test
    fun `a name that does not resolve asks about the address and the connection`() {
        val message = describeNetworkFailure(
            java.net.UnknownHostException("Unable to resolve host \"panel.example\""),
            host = "panel.example",
        )
        assertTrue(message.contains("Could not find panel.example"), message)
        assertTrue(message.contains("typo"), message)
        // A phone with no signal fails identically, and saying so saves
        // someone rewriting an address that was right all along.
        assertTrue(message.contains("online"), message)
    }

    @Test
    fun `a refused connection points at the port`() {
        val message = describeNetworkFailure(
            java.net.ConnectException("failed to connect to /10.0.0.1 (port 8080): ECONNREFUSED"),
            host = "panel.example",
        )
        assertTrue(message.contains("refused the connection"), message)
        assertTrue(message.contains("port"), message)
    }

    @Test
    fun `a timeout says whose fault it probably is, without quoting a figure`() {
        val message = describeNetworkFailure(
            java.net.SocketTimeoutException("timeout"),
            host = "panel.example",
        )
        assertTrue(message.contains("did not answer in time"), message)
        assertTrue(message.contains("not the app's"), message)
    }

    @Test
    fun `a timeout from any layer reads the same`() {
        val message = describeNetworkFailure(java.net.SocketTimeoutException(), host = "panel.example")
        assertTrue(message.contains("did not answer in time"), message)
        assertFalse(message.contains("0 seconds"), message)
    }

    @Test
    fun `a TLS failure suggests plain http, which is what panels run`() {
        val message = describeNetworkFailure(
            javax.net.ssl.SSLHandshakeException("Chain validation failed"),
            host = "panel.example",
        )
        assertTrue(message.contains("http://"), message)
    }

    @Test
    fun `an unreachable network names the network, not the address`() {
        val message = describeNetworkFailure(
            java.net.NoRouteToHostException("EHOSTUNREACH"),
            host = "panel.example",
        )
        assertTrue(message.contains("cannot be reached from this network"), message)
    }

    @Test
    fun `anything unrecognised still names the host and keeps the detail`() {
        val message = describeNetworkFailure(java.io.IOException("unexpected end of stream"), host = "panel.example")
        assertEquals("Could not reach panel.example: unexpected end of stream.", message)
    }

    @Test
    fun `no exception at all is still a sentence`() {
        assertEquals("Could not reach panel.example.", describeNetworkFailure(null, host = "panel.example"))
    }

    @Test
    fun `no message anywhere names the host and stops`() {
        assertEquals("Could not reach panel.example.", describeNetworkFailure(java.io.IOException(), host = "panel.example"))
    }
}
