package com.ipterebi.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ipterebi.app.AppContainer
import com.ipterebi.app.data.AccountState
import com.ipterebi.app.data.RecordingVolume
import com.ipterebi.app.data.recordingVolumes
import com.ipterebi.core.LiveStream
import com.ipterebi.core.StreamFormat
import com.ipterebi.core.UserInfo
import com.ipterebi.core.lineKey
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.XtreamException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val account: XtreamAccount? = null,
    /** Edited separately from the saved value so typing does not thrash DataStore. */
    val userAgentDraft: String = "",
    val checking: Boolean = false,
    val info: UserInfo? = null,
    val error: String? = null,
    val signedOut: Boolean = false,
    /** The tree uri recordings are written into, or empty when none is chosen. */
    val recordingFolder: String = "",
    /** Everywhere a recording could go on this box, with the room on each. */
    val recordingVolumes: List<RecordingVolume> = emptyList(),
    /** Channels hidden from every list, newest first, so they can be brought back. */
    val hiddenChannels: List<LiveStream> = emptyList(),
    /** A guide download is under way, whoever started it. */
    val guideDownloading: Boolean = false,
    /** When the full guide last arrived, in epoch millis; null when it never has. */
    val guideFetchedAt: Long? = null,
    /** The channel list has been asked for again and not yet come back. */
    val channelsRefreshing: Boolean = false,
) {
    val userAgentChanged: Boolean
        get() = account != null && userAgentDraft.trim() != account.userAgent
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // The folder a recording goes to, and how much room is on it. Per
            // device rather than per line: a stick belongs to the box.
            container.recordings.folder.collect { refreshFolder() }
        }
        viewModelScope.launch {
            container.credentials.state
                .filterIsInstance<AccountState.SignedIn>()
                .flatMapLatest { container.channelLists.hidden(it.account) }
                .collect { hidden -> _state.update { it.copy(hiddenChannels = hidden) } }
        }
        viewModelScope.launch {
            container.credentials.state
                .filterIsInstance<AccountState.SignedIn>()
                .collect { signedIn ->
                    _state.update { current ->
                        current.copy(
                            account = signedIn.account,
                            // Only seeded on first arrival — otherwise saving
                            // would yank the field out from under the cursor.
                            userAgentDraft = if (current.account == null) {
                                signedIn.account.userAgent
                            } else {
                                current.userAgentDraft
                            },
                        )
                    }
                    // Here as well as on the guide's own signals, because those
                    // fire first: a StateFlow hands over its current value the
                    // moment it is collected, which is before the account has
                    // arrived, and the age is read per line. Without this the
                    // panel said "the guide has not arrived yet" on a box
                    // holding 88,057 programmes — seen on the owner's own.
                    refreshGuideAge()
                }
        }
        // The guide's own progress, so the Refresh panel can say "fetching"
        // for the download rather than pretending it finished with the
        // channel list. It is shared, so this also lights up when the
        // background refresh runs on its own.
        viewModelScope.launch {
            container.guide.downloading.collect { busy ->
                _state.update { it.copy(guideDownloading = busy) }
                if (!busy) refreshGuideAge()
            }
        }
        viewModelScope.launch {
            container.guide.version.collect { refreshGuideAge() }
        }
    }

    private fun refreshGuideAge() {
        val account = _state.value.account ?: return
        viewModelScope.launch {
            val at = runCatching { container.guide.fetchedAt(account) }.getOrNull()
            _state.update { it.copy(guideFetchedAt = at) }
        }
    }

    /**
     * Saved immediately rather than behind an Apply button. The player keys its
     * ExoPlayer instance on the account, so this rebuilds it on the next open —
     * which is the whole point of having the toggle.
     */
    /** Picked by volume, not by folder: a television has no file picker. */
    fun setRecordingFolder(path: String) {
        viewModelScope.launch { container.recordings.setFolder(path) }
    }

    fun forgetRecordingFolder() {
        viewModelScope.launch { container.recordings.forgetFolder() }
    }

    private suspend fun refreshFolder() {
        val volumes = recordingVolumes(container.appContext)
        val chosen = container.recordings.folderNow()
        _state.update { it.copy(recordingFolder = chosen, recordingVolumes = volumes) }
    }

    fun setFormat(format: StreamFormat) {
        val account = _state.value.account ?: return
        if (account.format == format) return
        viewModelScope.launch { container.credentials.save(account.copy(format = format)) }
    }

    fun onUserAgentChange(value: String) = _state.update { it.copy(userAgentDraft = value) }

    fun applyUserAgent() {
        val account = _state.value.account ?: return
        val agent = _state.value.userAgentDraft.trim().ifBlank { XtreamAccount.DEFAULT_USER_AGENT }
        viewModelScope.launch {
            container.credentials.save(account.copy(userAgent = agent))
            _state.update { it.copy(userAgentDraft = agent) }
        }
    }

    /**
     * Re-runs the sign-in call and shows what came back. The point is not to
     * confirm the password — it is to see the connection count and the formats
     * the panel admits to, which is most of what you need when a channel will
     * not start.
     */
    fun recheck() {
        val account = _state.value.account ?: return
        viewModelScope.launch {
            _state.update { it.copy(checking = true, error = null, info = null) }
            try {
                val status = container.xtream.authenticate(account)
                status.clockShift?.let { container.guide.clock.statedBy(account.lineKey, it) }
                _state.update { it.copy(checking = false, info = status.info) }
            } catch (e: XtreamException) {
                _state.update { it.copy(checking = false, error = e.message) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(checking = false, error = e.message ?: "The check failed.") }
            }
        }
    }

    /**
     * Fetches the full guide now — the provider's own and every extra
     * source, in one pass.
     *
     * Here because of what changing the sources otherwise costs: a refresh
     * happens at most every twelve hours, so somebody who has just pasted
     * two addresses would see nothing change until tomorrow and conclude
     * they had typed them wrong.
     */
    fun refreshGuideNow() {
        val account = _state.value.account ?: return
        container.guide.refreshNow(account)
    }

    /**
     * Fetches the channel list and the guide again, now.
     *
     * **Asked for, and the reason is the provider rather than the app.** A
     * line's channels move: feeds are added, renamed and dropped, and a guide
     * is only as good as the download it came from. Until this, the only way
     * to see any of that was to wait — the guide refreshes at most every
     * twelve hours, and the channel list is fetched once and held for the
     * life of the process — or to force-stop the app, which is not something
     * anybody should have to know.
     *
     * Both halves together, because "refresh" means one thing to the person
     * pressing it. The guide half is [Guide.refreshNow]: whatever the network,
     * however recent the last one, whatever the last failure. The channel half
     * empties the shared cache and bumps its version, which is what makes the
     * screen the viewer came from go back for its own copy.
     *
     * The guide is the slow half — a minute or so on the box with five extra
     * sources — so nothing waits on it: it reports itself through
     * [Guide.downloading] and the screen says so.
     *
     * **One channel-list request, not two**, which the first version got
     * wrong and the box's own log showed: `get_live_streams` is 7.8 MB on this
     * line, and asking the guide to refresh while separately fetching the line
     * sent two of them at the same moment. The guide takes the channels when
     * it is given them — it needs them anyway, to know which entries to keep
     * and which channels hold a recording — so the fetch happens once and its
     * answer is handed on.
     */
    fun refreshEverything() {
        val account = _state.value.account ?: return
        _state.update { it.copy(channelsRefreshing = true, error = null) }
        container.lineChannels.refresh()
        viewModelScope.launch {
            // Asked for here as well as by the screen that will redraw, so the
            // panel can stop saying "fetching" when the line is actually back
            // — and so a line that refuses says so here rather than nowhere.
            val outcome = runCatching { container.lineChannels.all(account) }
            _state.update {
                it.copy(
                    channelsRefreshing = false,
                    error = outcome.exceptionOrNull()?.message ?: it.error,
                )
            }
            // Even when the line failed: the guide is worth having on its own,
            // and it will fetch the channels itself if it has to.
            container.guide.refreshNow(account, outcome.getOrNull())
        }
    }

    /** Brings a hidden channel back into every list it was taken out of. */
    fun showChannel(channel: LiveStream) {
        val account = _state.value.account ?: return
        viewModelScope.launch { container.channelLists.toggleHidden(account, channel) }
    }

    fun signOut() {
        val account = _state.value.account
        viewModelScope.launch {
            // Before the credentials go: the favourites and recents for a line
            // are keyed on it, and without the account there is no way to name
            // the keys to remove. Leaving them would hand the next line to sign
            // in a shelf of stream ids from somebody else's panel.
            account?.let {
                container.channelLists.clear(it)
                container.watched.clear(it)
                container.lists.clear(it)
                container.guide.forget(it)
            }
            container.credentials.clear()
            // Ids are a panel's private numbering, so anything still held here
            // would put this line's titles on the next line's ids.
            container.channels.publish(emptyList())
            container.films.publish(emptyList())
            container.series.publish(emptyList())
            container.episodes.publish(emptyList())
            _state.update { it.copy(signedOut = true) }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}

/** Free space, in the units a viewer deciding whether to record thinks in. */
fun asGigabytes(bytes: Long): String {
    val gb = bytes.toDouble() / (1024 * 1024 * 1024)
    return if (gb >= 1) String.format(java.util.Locale.ROOT, "%.1f GB", gb)
    else String.format(java.util.Locale.ROOT, "%d MB", bytes / (1024 * 1024))
}
