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
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ipterebi.app.playback.ActivePlayback
import com.ipterebi.app.ui.home.HomeChannels
import com.ipterebi.app.ui.theme.Appearance
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

    // Saving puts the field down. Without this the caret stays blinking in
    // it afterwards, so a saved value still reads as one being typed —
    // which is the owner's word for it: it should look locked in until it
    // is clicked again. Clearing focus is all it takes, because
    // DpadTextField ends editing when focus leaves.
    val focusManager = LocalFocusManager.current

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
            val context = LocalContext.current
            // What "Free the line" did, said under it until the screen is left.
            var freed by remember { mutableStateOf<String?>(null) }

            // First, as in Debritsu: the one setting that changes everything
            // else on the screen as it is pressed.
            // Not on the TV, which has the one look.
            if (Appearance.SWITCHABLE) Panel {
                SectionTitle("Appearance")
                ChoiceRow(
                    options = listOf(true to "Dark", false to "Light"),
                    isSelected = { it == Night.palette.dark },
                    onSelect = { dark -> Appearance.set(context, dark) },
                )
                Hint(
                    "Dark has the TV guide's colours; Light is pale with a dark blue " +
                        "accent. The player is dark in both.",
                )
            }

            Panel {
                SectionTitle("Home")
                ChoiceRow(
                    options = HomeChannels.Source.entries.map { it to it.label },
                    isSelected = { it == HomeChannels.source },
                    onSelect = { source -> HomeChannels.set(context, source) },
                )
                Hint(
                    "Which channels the home screen's Live TV row shows. Favourites " +
                        "is the list you starred; Recent is where you have just been.",
                )
            }

            Panel {
                SectionTitle("Your team")
                var team by remember { mutableStateOf(container.team.team.value) }
                DpadTextField(Modifier.fillMaxWidth()) { fieldModifier ->
                    OutlinedTextField(
                        value = team,
                        onValueChange = { team = it },
                        label = { Text("Team name") },
                        singleLine = true,
                        colors = fieldColours(),
                        shape = MaterialTheme.shapes.large,
                        modifier = fieldModifier.fillMaxWidth(),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SecondaryButton(
                        text = if (team != container.team.team.value) "Save" else "Saved",
                        onClick = {
                            container.team.set(team)
                            focusManager.clearFocus()
                        },
                        enabled = team != container.team.team.value,
                        height = 42.dp,
                    )
                }
                Hint(
                    "Home then lists every channel showing them, so when a feed goes " +
                        "bad there is somewhere to switch to without hunting for it. " +
                        "Type it the way your guide writes it — \"Man Utd\" and " +
                        "\"Manchester United\" are not the same to a provider. Leave it " +
                        "empty for no row.",
                )
            }

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
                        onClick = {
                            viewModel.applyUserAgent()
                            focusManager.clearFocus()
                        },
                        enabled = state.userAgentChanged,
                        height = 42.dp,
                    )
                }
                Hint(
                    "How the app identifies itself. If the channel list loads but every " +
                        "stream is refused, the panel is blocking the client — try another.",
                )
            }

            // Hiding has to be undoable from somewhere, or it is a trap: a
            // channel taken out of every list cannot be found again to bring
            // back. This is that somewhere, and it is why the hidden list
            // stores whole channels rather than ids — there would otherwise be
            // no name to draw here.
            if (state.hiddenChannels.isNotEmpty()) Panel {
                SectionTitle("Hidden channels")
                Hint(
                    "These are out of every list, out of search and out of the guide. " +
                        "Long-press a channel on Live TV to hide one.",
                )
                Spacer(Modifier.size(4.dp))
                state.hiddenChannels.forEach { channel ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = channel.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Night.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(
                            text = "Show",
                            onClick = { viewModel.showChannel(channel) },
                        )
                    }
                }
            }

            Panel {
                SectionTitle("Recording")
                // A volume, not a folder. Android TV has no file picker: the
                // box resolves OPEN_DOCUMENT_TREE to nothing at all and the
                // Google TV emulator to a stub that does nothing, both
                // measured. What works everywhere with no permission is the
                // app's own directory on each mounted volume, so the question
                // put to the viewer is the only one they have — the stick, or
                // the box.
                if (state.recordingVolumes.isEmpty()) {
                    Hint("No writable storage was found on this box.")
                } else {
                    ChoiceRow(
                        options = state.recordingVolumes.map {
                            it.path to "${it.label} · ${asGigabytes(it.freeBytes)} free"
                        },
                        isSelected = { it == state.recordingFolder },
                        onSelect = viewModel::setRecordingFolder,
                    )
                }
                Hint(
                    "Where recordings are written. A USB stick, in practice: this box " +
                        "has well under a gigabyte free, which is a few minutes of " +
                        "television. A recording takes the line while it runs, so " +
                        "nothing else can play at the same time.",
                )
            }

            Panel {
                SectionTitle("Check the line")
                Hint(
                    "Asks the panel what it thinks of this account right now. Free the " +
                        "line first to stop anything IPTerebi is playing — before " +
                        "watching on another device, such as a TV.",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    SecondaryButton(
                        text = "Free the line",
                        height = 46.dp,
                        enabled = !state.checking,
                        onClick = {
                            freed = if (ActivePlayback.stop()) {
                                "Stopped. Your provider may count the connection for " +
                                    "about 15 seconds more — wait that long before " +
                                    "starting the other device."
                            } else {
                                "Nothing was playing in IPTerebi. If the line is still " +
                                    "busy, another device is using it."
                            }
                            // What the panel says now, so "Connections" below shows
                            // whether the line really is free.
                            viewModel.recheck()
                        },
                    )
                }
                freed?.let { Hint(it) }

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
