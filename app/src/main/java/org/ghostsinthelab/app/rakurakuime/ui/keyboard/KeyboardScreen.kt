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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.ghostsinthelab.app.rakurakuime.R
import org.ghostsinthelab.app.rakurakuime.data.EmojiDictionary
import org.ghostsinthelab.app.rakurakuime.ui.InputMode
import org.ghostsinthelab.app.rakurakuime.ui.KeyboardViewModel
import org.ghostsinthelab.app.rakurakuime.ui.ShiftState
import org.ghostsinthelab.app.rakurakuime.ui.theme.KeyboardTheme

@Composable
fun KeyboardScreen(
    viewModel: KeyboardViewModel,
    // Read lazily: the IME service's currentInputConnection updates across
    // input sessions without recreating the ComposeView, so we must re-fetch
    // per callback invocation instead of capturing a snapshot.
    inputConnection: () -> InputConnection?,
    onKeyPress: () -> Unit,
    onSwitchIme: () -> Unit,
) {
    val composingText by viewModel.composingText.collectAsState()
    val pagedCandidates by viewModel.pagedCandidates.collectAsState()
    val hasPrev by viewModel.hasPrevPage.collectAsState()
    val hasNext by viewModel.hasNextPage.collectAsState()
    val isSelecting by viewModel.isSelecting.collectAsState()
    val inputMode by viewModel.inputMode.collectAsState()
    val shiftState by viewModel.shiftState.collectAsState()
    val isShifted = shiftState != ShiftState.NONE
    val heightScale by viewModel.keyboardHeightScale.collectAsState(initial = 1.0f)
    val splitLayoutLandscape by viewModel.splitLayoutLandscape.collectAsState(initial = true)
    val emojiCategory by viewModel.emojiCategory.collectAsState()
    val englishCandidates by viewModel.englishCandidates.collectAsState()
    val colors = KeyboardTheme.current

    val scaledKeyHeight = KeyboardLayout.KEY_HEIGHT * heightScale
    
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val shouldSplit = isLandscape && splitLayoutLandscape

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.keyboardBackground)
            .navigationBarsPadding()
            .padding(bottom = 8.dp)
    ) {
        // Candidate Bar — sources differ per mode. English uses the in-memory
        // trie (non-paginated); EZ uses the paged Room-backed candidate list.
        val isEnglish = inputMode == InputMode.ENGLISH
        CandidateBar(
            candidates = if (isEnglish) englishCandidates else pagedCandidates,
            hasPrev = if (isEnglish) false else hasPrev,
            hasNext = if (isEnglish) false else hasNext,
            onCandidateSelected = { candidate ->
                onKeyPress()
                if (isEnglish) {
                    val word = viewModel.selectEnglishCandidate(candidate)
                    inputConnection()?.commitText("$word ", 1)
                } else {
                    viewModel.selectCandidate(candidate)
                }
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
                            val keys = if (shouldSplit) {
                                val half = row.size / 2
                                val list = mutableListOf<Any>()
                                list.addAll(row.take(half))
                                list.add("SPACE")
                                list.addAll(row.drop(half))
                                list
                            } else {
                                row
                            }

                            for (item in keys) {
                                if (item is String && item == "SPACE") {
                                    Spacer(modifier = Modifier.weight(if (isLandscape) 2f else 1f))
                                } else if (item is KeyDefinition) {
                                    KeyButton(
                                        keyDef = item,
                                        isUppercase = isShifted,
                                        modifier = Modifier.weight(1f),
                                        keyHeight = scaledKeyHeight,
                                        useKeycapDrawable = true,
                                        onSwipeUp = {
                                            onKeyPress()
                                            inputConnection()?.commitText(item.qwertyChar.uppercase(), 1)
                                        },
                                        onAlternateSelected = { alt ->
                                            onKeyPress()
                                            inputConnection()?.commitText(if (isShifted) alt.uppercase() else alt.lowercase(), 1)
                                            // Auto-release one-shot shift after we've applied it.
                                            viewModel.consumeShift()
                                        },
                                        onClick = {
                                            onKeyPress()
                                            val selIndex = when(item.qwertyChar) {
                                                "1" -> 0; "2" -> 1; "3" -> 2; "4" -> 3; "5" -> 4
                                                "6" -> 5; "7" -> 6; "8" -> 7; "9" -> 8; "0" -> 9
                                                else -> -1
                                            }
                                            
                                            if (isSelecting && pagedCandidates.isNotEmpty() && selIndex != -1 && selIndex < pagedCandidates.size) {
                                                viewModel.selectCandidate(pagedCandidates[selIndex])
                                            } else {
                                                viewModel.onKeyPress(item.qwertyChar)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            InputMode.NUMBER -> {
                // Phone-dialer-style 3x4 number pad, filling the full keyboard width.
                Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf(",", "0", ".")
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
                                        inputConnection()?.commitText(char, 1)
                                    }
                                )
                            }
                        }
                    }
                }
            }
            InputMode.ENGLISH -> {
                // Commits a non-letter token (digit, punctuation, alternate)
                // into the editor. Flushes any in-progress English buffer
                // first so word prediction doesn't swallow the boundary.
                val commitNonLetter: (String) -> Unit = { token ->
                    val buffer = viewModel.commitEnglishBuffer()
                    if (buffer.isNotEmpty()) inputConnection()?.commitText(buffer, 1)
                    inputConnection()?.commitText(token, 1)
                }

                Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                    EnglishLayout.ROWS.forEachIndexed { rowIndex, row ->
                        // Normalise every row to ROW_WEIGHT so key widths line
                        // up across rows despite the differing key counts
                        // (13 / 12 / 11 / 11+shift). In split landscape we
                        // skip this — that layout deliberately breaks to two
                        // half-width halves with a centre spacer.
                        val shiftWeight = if (rowIndex == EnglishLayout.SHIFT_ROW_INDEX) EnglishLayout.SHIFT_WEIGHT else 0f
                        val rowKeyWeight = row.size.toFloat() + shiftWeight
                        val sidePad = if (shouldSplit) 0f
                            else ((EnglishLayout.ROW_WEIGHT - rowKeyWeight) / 2f).coerceAtLeast(0f)

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (sidePad > 0f) Spacer(modifier = Modifier.weight(sidePad))

                            // Shift key on the last row only:
                            //   NONE        → ⇧ on the default key background
                            //   SHIFTED     → ⇧ on the function-key background (one-shot)
                            //   CAPS_LOCK   → ⇪ on the function-key background (sticky)
                            if (rowIndex == EnglishLayout.SHIFT_ROW_INDEX) {
                                val shiftLabel = if (shiftState == ShiftState.CAPS_LOCK) "⇪" else "⇧"
                                val shiftActive = shiftState != ShiftState.NONE
                                val shiftDescription = stringResource(
                                    if (shiftState == ShiftState.CAPS_LOCK) R.string.a11y_key_caps_lock
                                    else R.string.a11y_key_shift
                                )
                                KeyButton(
                                    keyDef = KeyDefinition(shiftLabel),
                                    modifier = Modifier.weight(EnglishLayout.SHIFT_WEIGHT),
                                    keyHeight = scaledKeyHeight,
                                    backgroundColorOverride = if (shiftActive) colors.functionKeyBackground else colors.keyBackground,
                                    textColorOverride = if (shiftActive) colors.functionKeyTextColor else colors.keyTextColor,
                                    contentDescription = shiftDescription,
                                    onClick = {
                                        onKeyPress()
                                        viewModel.toggleShift()
                                    }
                                )
                            }

                            // Split-landscape: inject a centre spacer between
                            // the two halves of the row. Use Any so the marker
                            // sits alongside KeyDefinition entries.
                            val items: List<Any> = if (shouldSplit) {
                                val half = row.size / 2
                                buildList {
                                    addAll(row.take(half))
                                    add("__SPLIT__")
                                    addAll(row.drop(half))
                                }
                            } else {
                                row
                            }

                            for (item in items) {
                                when (item) {
                                    is String -> Spacer(
                                        modifier = Modifier.weight(if (isLandscape) 2f else 1f),
                                    )
                                    is KeyDefinition -> {
                                        val ch = item.qwertyChar
                                        val isLetter = ch.length == 1 && ch[0].isLetter()
                                        val display = if (isShifted && isLetter) ch.uppercase() else ch
                                        KeyButton(
                                            keyDef = KeyDefinition(display, "", item.alternates),
                                            modifier = Modifier.weight(1f),
                                            keyHeight = scaledKeyHeight,
                                            onAlternateSelected = { alt ->
                                                onKeyPress()
                                                commitNonLetter(alt)
                                                viewModel.consumeShift()
                                            },
                                            onClick = {
                                                onKeyPress()
                                                if (isLetter) {
                                                    // Accumulate into the composing buffer so
                                                    // the candidate bar can offer predictions.
                                                    viewModel.onEnglishKeyPress(display)
                                                } else {
                                                    // Digits and inline punctuation terminate
                                                    // the current word.
                                                    commitNonLetter(display)
                                                }
                                                // One-shot shift auto-releases after the key
                                                // that used it; CAPS_LOCK is sticky.
                                                viewModel.consumeShift()
                                            }
                                        )
                                    }
                                }
                            }

                            if (sidePad > 0f) Spacer(modifier = Modifier.weight(sidePad))
                        }
                    }
                }
            }
            InputMode.EMOJI -> {
                val emojiCtx = LocalContext.current
                var categories by remember { mutableStateOf<List<EmojiDictionary.Category>>(emptyList()) }
                LaunchedEffect(Unit) {
                    categories = EmojiDictionary.categories(emojiCtx)
                }

                // Cap the emoji area to half the screen height. Tabs stay pinned at
                // the top; the grid below them scrolls when content exceeds the cap.
                val maxEmojiAreaHeight = (configuration.screenHeightDp / 2).dp
                val tabRowHeight = scaledKeyHeight * 0.8f
                val maxGridHeight = maxEmojiAreaHeight - tabRowHeight - 8.dp

                val gridScrollState = rememberScrollState()
                LaunchedEffect(emojiCategory) {
                    gridScrollState.scrollTo(0)
                }

                Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                    // Category tabs (pinned).
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        categories.forEachIndexed { index, category ->
                            val isSelected = emojiCategory == index
                            val tabDescription = stringResource(
                                when (index) {
                                    0 -> R.string.a11y_emoji_tab_smileys
                                    1 -> R.string.a11y_emoji_tab_gestures
                                    2 -> R.string.a11y_emoji_tab_animals
                                    3 -> R.string.a11y_emoji_tab_food
                                    4 -> R.string.a11y_emoji_tab_travel
                                    5 -> R.string.a11y_emoji_tab_activities
                                    6 -> R.string.a11y_emoji_tab_objects
                                    else -> R.string.a11y_emoji_tab_symbols
                                }
                            )
                            KeyButton(
                                keyDef = KeyDefinition(category.tabIcon),
                                modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                                keyHeight = tabRowHeight,
                                backgroundColorOverride = if (isSelected) colors.functionKeyBackground else colors.keyBackground,
                                textColorOverride = if (isSelected) colors.functionKeyTextColor else colors.keyTextColor,
                                contentDescription = tabDescription,
                                onClick = { viewModel.setEmojiCategory(index) }
                            )
                        }
                    }

                    // Scrollable emoji grid, bounded to (half screen - tabs).
                    val emojis = categories.getOrNull(emojiCategory)?.emojis ?: emptyList()
                    val perRow = 8
                    Column(
                        modifier = Modifier
                            .heightIn(max = maxGridHeight)
                            .verticalScroll(gridScrollState)
                    ) {
                        emojis.chunked(perRow).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                for (emoji in row) {
                                    KeyButton(
                                        keyDef = KeyDefinition(emoji),
                                        modifier = Modifier.weight(1f),
                                        keyHeight = scaledKeyHeight,
                                        onClick = {
                                            onKeyPress()
                                            inputConnection()?.commitText(emoji, 1)
                                        }
                                    )
                                }
                                // Pad short trailing rows to keep cell width consistent.
                                repeat(perRow - row.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Function Row
        FunctionRow(
            inputMode = inputMode,
            isShifted = isShifted,
            keyHeight = scaledKeyHeight,
            shouldSplit = shouldSplit,
            onBackspace = {
                onKeyPress()
                val handledByBuffer = if (inputMode == InputMode.ENGLISH) {
                    viewModel.onEnglishBackspace()
                } else {
                    viewModel.onBackspace()
                }
                if (!handledByBuffer) {
                    inputConnection()?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                    inputConnection()?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                }
            },
            onSpace = {
                onKeyPress()
                if (inputMode == InputMode.ENGLISH) {
                    val buffer = viewModel.commitEnglishBuffer()
                    if (buffer.isNotEmpty()) {
                        inputConnection()?.commitText(buffer, 1)
                    }
                    inputConnection()?.commitText(" ", 1)
                } else if (composingText.isNotEmpty() || viewModel.preEditBuffer.value.isNotEmpty()) {
                    val wasAlreadySelecting = isSelecting
                    val canSelect = viewModel.enterSelectionMode()

                    if (wasAlreadySelecting || !canSelect) {
                        val textToCommit = viewModel.commitAll()
                        if (textToCommit.isNotEmpty()) {
                            inputConnection()?.commitText(textToCommit, 1)
                        } else {
                            inputConnection()?.commitText(" ", 1)
                        }
                    }
                } else {
                    inputConnection()?.commitText(" ", 1)
                }
            },
            onEnter = {
                onKeyPress()
                val committed = if (inputMode == InputMode.ENGLISH) {
                    val buffer = viewModel.commitEnglishBuffer()
                    if (buffer.isNotEmpty()) {
                        inputConnection()?.commitText(buffer, 1)
                        true
                    } else false
                } else {
                    val textToCommit = viewModel.commitAll()
                    if (textToCommit.isNotEmpty()) {
                        inputConnection()?.commitText(textToCommit, 1)
                        true
                    } else false
                }
                if (!committed) {
                    inputConnection()?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    inputConnection()?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                }
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
