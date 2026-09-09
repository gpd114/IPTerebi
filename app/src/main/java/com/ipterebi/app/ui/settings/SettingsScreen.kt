package com.ipterebi.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ipterebi.app.AppContainer
import com.ipterebi.core.StreamFormat
import com.ipterebi.core.UserAgents
import com.ipterebi.core.connectionsLabel
import com.ipterebi.core.expiryLabel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            val account = state.account

            SectionTitle("Line")
            Text(
                text = account?.base ?: "—",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = account?.username ?: "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(20.dp))

            SectionTitle("Stream format")
            Text(
                "Which container the panel is asked for. Applies the next time a " +
                    "channel is opened. If channels fail instantly or never start, " +
                    "this is the first thing to change.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StreamFormat.entries.forEach { format ->
                    FilterChip(
                        selected = account?.format == format,
                        onClick = { viewModel.setFormat(format) },
                        label = { Text(format.label) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(20.dp))

            SectionTitle("User agent")
            Text(
                "How the app identifies itself. If the channel list loads but every " +
                    "stream is refused, the panel is blocking the client — try another.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UserAgents.presets.forEach { (label, value) ->
                    FilterChip(
                        selected = state.userAgentDraft.trim() == value,
                        onClick = { viewModel.onUserAgentChange(value) },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.userAgentDraft,
                onValueChange = viewModel::onUserAgentChange,
                label = { Text("Sent as User-Agent") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = viewModel::applyUserAgent,
                enabled = state.userAgentChanged,
            ) {
                Text(if (state.userAgentChanged) "Apply" else "Saved")
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(20.dp))

            SectionTitle("Check the line")
            Text(
                "Asks the panel what it thinks of this account right now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = viewModel::recheck, enabled = !state.checking) {
                if (state.checking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                }
                Text(if (state.checking) "Checking…" else "Check now")
            }

            state.info?.let { info ->
                Spacer(Modifier.height(12.dp))
                Fact("Status", info.status.ifBlank { "—" })
                Fact("Expires", info.expiryLabel())
                info.connectionsLabel().takeIf { it.isNotBlank() }?.let { Fact("Connections", it) }
                Fact(
                    "Panel offers",
                    info.allowedOutputFormats.takeIf { it.isNotEmpty() }?.joinToString(", ")
                        ?: "it does not say",
                )
            }

            state.error?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(28.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = viewModel::signOut,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Sign out") }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Fact(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}
