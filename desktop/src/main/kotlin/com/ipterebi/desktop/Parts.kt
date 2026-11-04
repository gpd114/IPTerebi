package com.ipterebi.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width

/**
 * The same pieces `ui/NightParts.kt` gives the Android build, in the few
 * shapes this screen needs: a panel with a rim, a solid accent button, a quiet
 * one, a row of choices with the chosen one filled, and a field.
 *
 * Named after theirs on purpose. Anything added here should keep to the same
 * rules -- flat fills, one colour per role, the rim on a card, corners from
 * [Corners] rather than a literal at the call site.
 */

/** A card: the veil fill, and the one-pixel rim that gives it an edge. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier
            .background(Ink.veil, Corners.panel)
            .border(1.dp, Ink.hairline, Corners.panel)
            .padding(padding)
    ) {
        Column(content = content)
    }
}


/** The one thing on a screen that is the thing to press. */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .background(if (enabled) Ink.cobalt else Ink.quiet, Corners.control)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) Color.White else Ink.inkSoft,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Quieter: glass rather than the accent, for anything that is not the point. */
@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .background(Ink.quiet, Corners.control)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Ink.ink, fontSize = 14.sp)
    }
}

/**
 * A row of choices with the chosen one solid, as the Android `ChoiceRow` does.
 * The label takes its colour from the fill rather than always being white --
 * the lesson from the grey experiment, where a white label on a pale fill
 * washed out.
 */
@Composable
fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    chosen: T,
    name: (T) -> String,
    onChoose: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = Ink.inkSoft, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row {
            options.forEach { option ->
                val picked = option == chosen
                Box(
                    Modifier
                        .background(if (picked) Ink.cobalt else Ink.quiet, Corners.control)
                        .clickable { onChoose(option) }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text(
                        name(option),
                        color = if (picked) Color.White else Ink.ink,
                        fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

/** A labelled field on the dark page. */
@Composable
fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    secret: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text(label, color = Ink.inkSoft, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation =
                if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
            shape = Corners.control,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Ink.field,
                unfocusedContainerColor = Ink.field,
                focusedTextColor = Ink.ink,
                unfocusedTextColor = Ink.ink,
                cursorColor = Ink.accent,
                focusedIndicatorColor = Ink.cobalt,
                unfocusedIndicatorColor = Ink.edge,
            ),
        )
    }
}
