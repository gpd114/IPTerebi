package com.ipterebi.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ipterebi.app.AppContainer
import com.ipterebi.app.data.AccountState
import com.ipterebi.core.StreamFormat
import com.ipterebi.core.UserInfo
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.XtreamException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
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
) {
    val userAgentChanged: Boolean
        get() = account != null && userAgentDraft.trim() != account.userAgent
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
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
                }
        }
    }

    /**
     * Saved immediately rather than behind an Apply button. The player keys its
     * ExoPlayer instance on the account, so this rebuilds it on the next open —
     * which is the whole point of having the toggle.
     */
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
                val info = container.xtream.authenticate(account)
                _state.update { it.copy(checking = false, info = info) }
            } catch (e: XtreamException) {
                _state.update { it.copy(checking = false, error = e.message) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(checking = false, error = e.message ?: "The check failed.") }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            container.credentials.clear()
            container.channels.publish(emptyList())
            _state.update { it.copy(signedOut = true) }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}
