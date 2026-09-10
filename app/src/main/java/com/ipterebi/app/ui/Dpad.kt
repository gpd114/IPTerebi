package com.ipterebi.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp

/**
 * Remote-control support: everything a D-pad needs that a finger does not.
 *
 * Nothing here changes how the app behaves under touch. Compose tracks whether
 * the user is touching the screen or pressing keys — [InputMode] — and each
 * piece below only acts in keyboard mode, which is the mode a TV remote, a
 * D-pad, and a hardware keyboard all put it in.
 */

/** Keys a remote sends to mean "select this". */
private val activateKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * Draws a ring around an element while it has focus.
 *
 * Under touch nothing is ever focused, so this never draws. Under a remote it is
 * the only way to know where you are: Material's own focus indication is a faint
 * tint made for a keyboard at arm's length, and invisible from a sofa.
 *
 * Place it *before* `clickable` in the chain, so that it observes the focus of
 * the element it outlines.
 */
fun Modifier.focusRing(shape: Shape = RoundedCornerShape(8.dp)): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val colour = MaterialTheme.colorScheme.primary
    this
        .onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(3.dp, colour, shape) else Modifier)
}

/**
 * A text field that behaves on a remote: click to edit.
 *
 * Two things go wrong with a plain text field under a D-pad, and both were
 * measured on an emulator rather than guessed:
 *
 * - **It traps the arrow keys.** A focused field consumes up and down, keyboard
 *   open or not, so focus goes in and never comes out. On the sign-in screen that
 *   is the first field and nothing after it.
 * - **Passing over it summons the keyboard.** Focus arriving is enough, so moving
 *   up a channel list through the search box would throw a keyboard over the
 *   screen every time.
 *
 * So under a remote the field itself cannot take focus until asked. Focus lands
 * on the wrapper, which draws a ring; the centre button starts editing, which is
 * when the keyboard is wanted; and up or down leave, whether editing or not.
 * Under touch the wrapper is inert and the field is an ordinary field.
 *
 * [content] receives the modifier to put on the text field.
 */
@Composable
fun DpadTextField(
    modifier: Modifier = Modifier,
    content: @Composable (fieldModifier: Modifier) -> Unit,
) {
    val inputModes = LocalInputModeManager.current
    val focusManager = LocalFocusManager.current
    val field = remember { FocusRequester() }
    var editing by remember { mutableStateOf(false) }
    var wrapperFocused by remember { mutableStateOf(false) }
    val ring = MaterialTheme.colorScheme.primary

    // Read when focus is being decided, never captured when the screen was
    // drawn. The mode changes on the input itself — a tap makes it touch, a key
    // makes it keyboard — before anything has recomposed, so a captured value
    // is one input out of date exactly when it matters: the first tap after
    // using a remote, or the first key after a tap.
    fun remote() = inputModes.inputMode == InputMode.Keyboard

    // After the recomposition that made the field focusable, not during it.
    LaunchedEffect(editing) {
        if (editing) field.requestFocus()
    }

    Box(
        modifier
            // All before focusable(): key and focus events reach the focused
            // element and then its ancestors, and a modifier placed after the
            // focus target is inside it rather than above it.
            .onFocusChanged { wrapperFocused = it.isFocused }
            .onKeyEvent { event ->
                if (remote() && !editing && event.type == KeyEventType.KeyDown && event.key in activateKeys) {
                    editing = true
                    true
                } else {
                    false
                }
            }
            .focusProperties { canFocus = remote() && !editing }
            .focusable()
            .then(if (wrapperFocused) Modifier.border(3.dp, ring, RoundedCornerShape(6.dp)) else Modifier),
    ) {
        content(
            Modifier
                .focusRequester(field)
                .focusProperties { canFocus = !remote() || editing }
                // Focus arriving by any route — a tap included — counts as
                // editing, and editing lasts until focus leaves. Without that,
                // a tablet with a keyboard broke: tap the field, type, and the
                // first key switched the mode to keyboard, made the field
                // unfocusable, and took it away after one letter.
                .onFocusChanged { state ->
                    if (state.isFocused) editing = true
                    else if (editing) editing = false
                }
                .onPreviewKeyEvent { event ->
                    // Up and down always leave. Left and right stay with the
                    // field, where they move the cursor.
                    val direction = when (event.key) {
                        Key.DirectionDown -> FocusDirection.Down
                        Key.DirectionUp -> FocusDirection.Up
                        else -> return@onPreviewKeyEvent false
                    }
                    if (event.type == KeyEventType.KeyDown) focusManager.moveFocus(direction)
                    // The key-up is consumed too, or the field sees half a press.
                    true
                },
        )
    }
}
