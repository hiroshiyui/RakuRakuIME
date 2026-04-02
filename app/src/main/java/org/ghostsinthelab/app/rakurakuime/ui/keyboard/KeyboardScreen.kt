/*
 * RakuRaku IME - EZ Input Method for Android
 * Copyright (C) 2026  RakuRaku IME Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
    val heightScale by viewModel.keyboardHeightScale.collectAsState(initial = 1.0f)
    val colors = KeyboardTheme.current

    val scaledKeyHeight = KeyboardLayout.KEY_HEIGHT * heightScale

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
                                    keyHeight = scaledKeyHeight,
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
                                    keyHeight = scaledKeyHeight,
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
            keyHeight = scaledKeyHeight,
            onBackspace = {
                onKeyPress()
                val handledByBuffer = viewModel.onBackspace()
                if (!handledByBuffer) {
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
                            currentInputConnection?.commitText(" ", 1)
                        }
                    }
                } else {
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
