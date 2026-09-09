package com.ipterebi.core

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class LineSummaryTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun `an expiry timestamp becomes a readable date`() {
        assertEquals(
            "1 Jan 2027",
            UserInfo(expiryEpochSeconds = "1798761600").expiryLabel(utc),
        )
    }

    @Test
    fun `an absent expiry is not rendered as 1970`() {
        assertEquals("No expiry", UserInfo(expiryEpochSeconds = "").expiryLabel(utc))
        assertEquals("No expiry", UserInfo(expiryEpochSeconds = "0").expiryLabel(utc))
        assertEquals("No expiry", UserInfo(expiryEpochSeconds = "null").expiryLabel(utc))
    }

    @Test
    fun `connections read as a sentence`() {
        assertEquals(
            "1 of 2 connections in use",
            UserInfo(maxConnections = "2", activeConnections = "1").connectionsLabel(),
        )
    }

    @Test
    fun `a panel that says nothing about connections gets no line`() {
        assertEquals("", UserInfo().connectionsLabel())
    }
}
