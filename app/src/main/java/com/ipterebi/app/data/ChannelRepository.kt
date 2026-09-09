package com.ipterebi.app.data

import com.ipterebi.core.LiveStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The channel list currently on screen, held here so the player can look a
 * channel up by id.
 *
 * The player is navigated to with a stream id and nothing else, deliberately.
 * A full channel list is tens of thousands of entries on a large line, and
 * navigation arguments end up in a Bundle that is written to the saved instance
 * state — putting the list there risks a TransactionTooLargeException on a
 * process death that is impossible to reproduce on the machine it was written
 * on. An integer is always safe.
 */
class ChannelRepository {

    private val _channels = MutableStateFlow<List<LiveStream>>(emptyList())
    val channels: StateFlow<List<LiveStream>> = _channels.asStateFlow()

    fun publish(channels: List<LiveStream>) {
        _channels.value = channels
    }

    fun find(streamId: Int): LiveStream? =
        _channels.value.firstOrNull { it.streamId == streamId }
}
