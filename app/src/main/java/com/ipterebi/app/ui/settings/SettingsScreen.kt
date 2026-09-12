package com.ipterebi.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ipterebi.app.AppContainer
import com.ipterebi.app.ui.ChoiceRow
import com.ipterebi.app.ui.DpadTextField
import com.ipterebi.app.ui.Panel
import com.ipterebi.app.ui.PrimaryButton
import com.ipterebi.app.ui.ScreenTopBar
import com.ipterebi.app.ui.SecondaryButton
import com.ipterebi.app.ui.fieldColours
import com.ipterebi.app.ui.theme.Night
import com.ipterebi.core.StreamFormat
import com.ipterebi.core.UserAgents
import com.ipterebi.core.connectionsLabel
import com.ipterebi.core.expiryLabel

/**
 * Settings, as panels — one per thing that can be changed — on Debritsu's
 * pattern: a choice is a row of pills with the chosen one solid, the one
 * action a panel offers is the solid button.
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.signedOut) {
        if (state.signedOut) onSignedOut()
    }

    Scaffold(
        topBar = { ScreenTopBar("Settings", onBack) },
        containerColor = Night.ground,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val account = state.account

            Panel {
                SectionTitle("Line")
                Column {
                    Text(
                        text = account?.base ?: "—",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Night.ink,
                    )
                    Text(
                        text = account?.username ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = Night.inkSoft,
                    )
                }
            }

            Panel {
                SectionTitle("Stream format")
                ChoiceRow(
                    options = StreamFormat.entries.map { it to it.label },
                    isSelected = { it == account?.format },
                    onSelect = viewModel::setFormat,
                )
                Hint(
                    "Which container the panel is asked for. Applies the next time a " +
                        "channel is opened. If channels fail instantly or never start, " +
                        "this is the first thing to change.",
                )
            }

            Panel {
                SectionTitle("User agent")
                ChoiceRow(
                    options = UserAgents.presets.map { (label, value) -> value to label },
                    isSelected = { state.userAgentDraft.trim() == it },
                    onSelect = viewModel::onUserAgentChange,
                )
                DpadTextField(Modifier.fillMaxWidth()) { fieldModifier ->
                    OutlinedTextField(
                        value = state.userAgentDraft,
                        onValueChange = viewModel::onUserAgentChange,
                        label = { Text("Sent as User-Agent") },
                        singleLine = true,
                        colors = fieldColours(),
                        shape = MaterialTheme.shapes.large,
                        modifier = fieldModifier.fillMaxWidth(),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SecondaryButton(
                        text = if (state.userAgentChanged) "Apply" else "Saved",
                        onClick = viewModel::applyUserAgent,
                        enabled = state.userAgentChanged,
                        height = 42.dp,
                    )
                }
                Hint(
                    "How the app identifies itself. If the channel list loads but every " +
                        "stream is refused, the panel is blocking the client — try another.",
                )
            }

            Panel {
                SectionTitle("Check the line")
                Hint("Asks the panel what it thinks of this account right now.")
                PrimaryButton(
                    onClick = viewModel::recheck,
                    enabled = !state.checking,
                    height = 46.dp,
                ) {
                    if (state.checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = LocalContentColor.current,
                        )
                        Spacer(Modifier.size(10.dp))
                    }
                    Text(
                        if (state.checking) "Checking…" else "Check now",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                state.info?.let { info ->
                    Column {
                        Fact("Status", info.status.ifBlank { "—" })
                        Fact("Expires", info.expiryLabel())
                        info.connectionsLabel().takeIf { it.isNotBlank() }?.let { Fact("Connections", it) }
                        Fact(
                            "Panel offers",
                            info.allowedOutputFormats.takeIf { it.isNotEmpty() }?.joinToString(", ")
                                ?: "it does not say",
                        )
                    }
                }

                state.error?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            SecondaryButton(
                text = "Sign out",
                onClick = viewModel::signOut,
                colour = Night.pink,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleLarge, color = Night.ink)
}

/** Small print under a control: what it does, in a sentence or two. */
@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Night.inkSoft)
}

@Composable
private fun Fact(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Night.inkSoft,
            modifier = Modifier.width(120.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = Night.ink)
    }
}
