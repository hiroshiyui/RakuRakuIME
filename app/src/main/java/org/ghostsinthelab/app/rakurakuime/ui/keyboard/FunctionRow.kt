package org.ghostsinthelab.app.rakurakuime.ui.keyboard

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.ghostsinthelab.app.rakurakuime.ui.InputMode
import org.ghostsinthelab.app.rakurakuime.ui.theme.KeyboardTheme

@Composable
fun FunctionRow(
    inputMode: InputMode,
    isShifted: Boolean,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onEnter: () -> Unit,
    onToggleShift: () -> Unit,
    onToggleMode: (InputMode) -> Unit,
    onSwitchIme: () -> Unit,
) {
    val colors = KeyboardTheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Mode toggle (?123)
        KeyButton(
            keyDef = KeyDefinition(if (inputMode == InputMode.EZ) "?123" else "EZ"),
            modifier = Modifier.weight(1.5f),
            backgroundColorOverride = colors.functionKeyBackground,
            textColorOverride = colors.functionKeyTextColor,
            onClick = {
                if (inputMode == InputMode.EZ) onToggleMode(InputMode.NUMBER)
                else onToggleMode(InputMode.EZ)
            }
        )

        // Switch IME (Globe)
        KeyButton(
            keyDef = KeyDefinition("🌐"),
            modifier = Modifier.weight(1f),
            backgroundColorOverride = colors.functionKeyBackground,
            textColorOverride = colors.functionKeyTextColor,
            onClick = onSwitchIme
        )

        // Space
        KeyButton(
            keyDef = KeyDefinition(" ", "Space"),
            modifier = Modifier.weight(4f),
            onClick = onSpace
        )

        // Backspace
        KeyButton(
            keyDef = KeyDefinition("⌫"),
            modifier = Modifier.weight(1.5f),
            backgroundColorOverride = colors.functionKeyBackground,
            textColorOverride = colors.functionKeyTextColor,
            onClick = onBackspace
        )

        // Enter
        KeyButton(
            keyDef = KeyDefinition("⏎"),
            modifier = Modifier.weight(1.5f),
            backgroundColorOverride = colors.functionKeyBackground,
            textColorOverride = colors.functionKeyTextColor,
            onClick = onEnter
        )
    }
}
