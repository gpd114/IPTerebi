package com.ipterebi.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** The letters on a channel's tile when it has no logo, from names shaped like real lines'. */
class ChannelInitialsTest {

    @Test
    fun `two words give their first letters`() {
        assertEquals("TN", channelInitials("Test News"))
    }

    @Test
    fun `a country or group prefix is dropped, so a category is not all the same letters`() {
        assertEquals("BO", channelInitials("UK: BBC One"))
        assertEquals("A1", channelInitials("SPORT | Arena 1"))
        assertEquals("CM", channelInitials("UK | ENT | Channel Mix"))
    }

    @Test
    fun `quality tags are not letters worth showing`() {
        assertEquals("TN", channelInitials("Test News HD"))
        assertEquals("SO", channelInitials("UK: Sky One FHD"))
        assertEquals("NA", channelInitials("4K Nature"))
    }

    @Test
    fun `one word gives its first two letters`() {
        assertEquals("ME", channelInitials("Metro"))
    }

    @Test
    fun `letters outside English are kept`() {
        assertEquals("テレ", channelInitials("JP: テレビ"))
        assertEquals("ÉC", channelInitials("FR | école canal"))
    }

    @Test
    fun `a name with nothing usable still gets something`() {
        assertEquals("TV", channelInitials(""))
        assertEquals("TV", channelInitials("UK: HD"))
        assertEquals("TV", channelInitials("| :"))
    }
}
