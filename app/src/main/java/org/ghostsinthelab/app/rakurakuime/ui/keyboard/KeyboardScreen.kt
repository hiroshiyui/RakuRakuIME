package org.ghostsinthelab.app.rakurakuime.ui.keyboard

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.ghostsinthelab.app.rakurakuime.ui.InputMode
import org.ghostsinthelab.app.rakurakuime.ui.KeyboardViewModel
import org.ghostsinthelab.app.rakurakuime.ui.theme.KeyboardTheme

@Composable
fun KeyboardScreen(
    viewModel: KeyboardViewModel,
    currentInputConnection: InputConnection?,
    onKeyPress: () -> Unit,
    onSwitchIme: () -> Unit,
) {
    val composingText by viewModel.composingText.collectAsState()
    val pagedCandidates by viewModel.pagedCandidates.collectAsState()
    val hasPrev by viewModel.hasPrevPage.collectAsState()
    val hasNext by viewModel.hasNextPage.collectAsState()
    val isSelecting by viewModel.isSelecting.collectAsState()
    val inputMode by viewModel.inputMode.collectAsState()
    val isShifted by viewModel.isShifted.collectAsState()
    val colors = KeyboardTheme.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.keyboardBackground)
            .navigationBarsPadding()
            .padding(bottom = 8.dp)
    ) {
        // Candidate Bar
        CandidateBar(
            candidates = pagedCandidates,
            hasPrev = hasPrev,
            hasNext = hasNext,
            onCandidateSelected = { candidate ->
                onKeyPress()
                viewModel.selectCandidate(candidate)
            },
            onPrevPage = { viewModel.prevPage() },
            onNextPage = { viewModel.nextPage() }
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Main Keyboard
        when (inputMode) {
            InputMode.EZ -> {
                Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                    for (row in KeyboardLayout.ROWS) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            for (key in row) {
                                KeyButton(
                                    keyDef = key,
                                    isUppercase = isShifted,
                                    modifier = Modifier.weight(1f),
                                    onSwipeUp = {
                                        onKeyPress()
                                        currentInputConnection?.commitText(key.qwertyChar.uppercase(), 1)
                                    },
                                    onClick = {
                                        onKeyPress()
                                        val selIndex = when(key.qwertyChar) {
                                            "1" -> 0; "2" -> 1; "3" -> 2; "4" -> 3; "5" -> 4
                                            "6" -> 5; "7" -> 6; "8" -> 7; "9" -> 8; "0" -> 9
                                            else -> -1
                                        }
                                        
                                        // If in Selection Mode, numbers select candidates.
                                        // Otherwise, they are roots.
                                        if (isSelecting && pagedCandidates.isNotEmpty() && selIndex != -1 && selIndex < pagedCandidates.size) {
                                            viewModel.selectCandidate(pagedCandidates[selIndex])
                                        } else {
                                            viewModel.onKeyPress(key.qwertyChar)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            InputMode.NUMBER -> {
                Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                    val rows = listOf(
                        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                        listOf("-", "/", ":", ";", "(", ")", "$", "&", "@", "\""),
                        listOf(".", ",", "?", "!", "'")
                    )
                    for (row in rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            for (char in row) {
                                KeyButton(
                                    keyDef = KeyDefinition(char),
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        onKeyPress()
                                        currentInputConnection?.commitText(char, 1)
                                    }
                                )
                            }
                        }
                    }
                }
            }
            else -> {}
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Function Row
        FunctionRow(
            inputMode = inputMode,
            isShifted = isShifted,
            onBackspace = {
                onKeyPress()
                val handledByBuffer = viewModel.onBackspace()
                if (!handledByBuffer) {
                    // Send standard backspace to the text field
                    currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                    currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                }
            },
            onSpace = {
                onKeyPress()
                if (composingText.isNotEmpty() || viewModel.preEditBuffer.value.isNotEmpty()) {
                    val wasAlreadySelecting = isSelecting
                    val canSelect = viewModel.enterSelectionMode()
                    
                    if (wasAlreadySelecting || !canSelect) {
                        val textToCommit = viewModel.commitAll()
                        if (textToCommit.isNotEmpty()) {
                            currentInputConnection?.commitText(textToCommit, 1)
                        } else {
                            // Should not happen if check passed, but fallback to sending space
                            currentInputConnection?.commitText(" ", 1)
                        }
                    }
                } else {
                    // Send standard space to the text field
                    currentInputConnection?.commitText(" ", 1)
                }
            },
            onEnter = {
                onKeyPress()
                val textToCommit = viewModel.commitAll()
                if (textToCommit.isNotEmpty()) {
                    currentInputConnection?.commitText(textToCommit, 1)
                }
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            },
            onToggleShift = { 
                onKeyPress()
                viewModel.toggleShift() 
            },
            onToggleMode = { mode -> 
                onKeyPress()
                viewModel.setInputMode(mode) 
            },
            onSwitchIme = onSwitchIme
        )
    }
}
