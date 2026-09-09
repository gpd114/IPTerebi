package com.ipterebi.app.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ipterebi.app.AppContainer
import com.ipterebi.core.StreamFormat

@Composable
fun LoginScreen(
    container: AppContainer,
    onSignedIn: () -> Unit,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.factory(container)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.signedIn) {
        if (state.signedIn) onSignedIn()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("IPTerebi", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Sign in to an Xtream Codes line.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = state.address,
            onValueChange = viewModel::onAddressChange,
            label = { Text("Server address") },
            placeholder = { Text("line.example.com:8080") },
            supportingText = {
                Text("A full player_api.php or get.php link works too — it fills in the rest.")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            label = { Text("Username") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation =
                if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector =
                            if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))

        Text("Stream format", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StreamFormat.entries.forEach { format ->
                FilterChip(
                    selected = state.format == format,
                    onClick = { viewModel.onFormatChange(format) },
                    label = { Text(format.label) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "MPEG-TS works on nearly every panel. Try HLS if channels stutter.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.error != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = state.error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = viewModel::signIn,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(12.dp))
            }
            Text(if (state.busy) "Checking…" else "Sign in")
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "IPTerebi ships no channels and no sources. You supply your own line.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}
