package com.ipterebi.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ipterebi.app.AppContainer
import com.ipterebi.core.StreamFormat
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.XtreamException
import com.ipterebi.core.XtreamUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val address: String = "",
    val username: String = "",
    val password: String = "",
    val format: StreamFormat = StreamFormat.TS,
    val busy: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
)

class LoginViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    /**
     * Pasting a `get.php` or `player_api.php` URL fills the username and
     * password in as a side effect — those URLs carry both, and asking someone
     * to pick them out of a query string by hand is a needless way to lose them
     * at the first screen.
     */
    fun onAddressChange(value: String) {
        val parsed = XtreamUrl.parse(value)
        _state.update { current ->
            current.copy(
                address = value,
                username = parsed?.username ?: current.username,
                password = parsed?.password ?: current.password,
                error = null,
            )
        }
    }

    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun onFormatChange(format: StreamFormat) = _state.update { it.copy(format = format) }

    fun signIn() {
        val current = _state.value
        val endpoint = XtreamUrl.parse(current.address)
        if (endpoint == null) {
            _state.update { it.copy(error = "That does not look like a server address.") }
            return
        }

        val username = (endpoint.username ?: current.username).trim()
        val password = endpoint.password ?: current.password
        if (username.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "A username and password are needed.") }
            return
        }

        val account = XtreamAccount(
            base = endpoint.base,
            username = username,
            password = password,
            format = current.format,
        )

        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                // Checked before storing, so a typo cannot be saved and then
                // surface later as an unexplained failure on the channel list.
                container.xtream.authenticate(account)
                container.credentials.save(account)
                _state.update { it.copy(busy = false, signedIn = true) }
            } catch (e: XtreamException) {
                _state.update { it.copy(busy = false, error = e.message) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "Sign-in failed.") }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { LoginViewModel(container) }
        }
    }
}
