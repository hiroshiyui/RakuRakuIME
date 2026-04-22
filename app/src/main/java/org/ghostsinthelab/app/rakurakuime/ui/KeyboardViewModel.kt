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

package org.ghostsinthelab.app.rakurakuime.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ghostsinthelab.app.rakurakuime.data.EnglishDictionary
import org.ghostsinthelab.app.rakurakuime.data.ImeDatabase
import org.ghostsinthelab.app.rakurakuime.data.UserPreferences

/**
 * Shift-key state machine.
 *
 * * [NONE] — default, letters render lowercase.
 * * [SHIFTED] — "one-shot": next letter/alternate is uppercase, then
 *   auto-releases back to [NONE] via [KeyboardViewModel.consumeShift].
 * * [CAPS_LOCK] — sticky uppercase, stays until the user taps shift again.
 *
 * The shift key cycles NONE -> SHIFTED -> CAPS_LOCK -> NONE, so a quick
 * double-tap lands in [CAPS_LOCK] and a single tap remains one-shot.
 */
enum class ShiftState { NONE, SHIFTED, CAPS_LOCK }

/**
 * Supported input modes for the RakuRaku IME.
 */
enum class InputMode {
    /** Primary radical-based input mode. */
    EZ,
    /** Numeric and special character input. */
    NUMBER,
    /** English text input with word prediction. */
    ENGLISH,
    /** Emoji selection mode. */
    EMOJI
}

/**
 * The central state manager for the keyboard UI.
 *
 * This ViewModel handles the logic for:
 * - Tracking the current [InputMode].
 * - Managing the composing buffer for both EZ and English modes.
 * - Fetching and paging candidates from the database.
 * - Handling user interactions like key presses, backspaces, and selections.
 * - Exposing user preferences (vibration, height, etc.) to the UI.
 */
class KeyboardViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ImeDatabase.getDatabase(application)
    private val userPreferences = UserPreferences(application)
    private val appContext = application.applicationContext

    /** Whether vibration is enabled for key presses. */
    val vibrationEnabled = userPreferences.vibrationEnabled
    /** The intensity of the haptic feedback. */
    val vibrationIntensity = userPreferences.vibrationIntensity
    /** The scaling factor for the keyboard's height. */
    val keyboardHeightScale = userPreferences.keyboardHeightScale
    /** Whether to use a split layout in landscape mode. */
    val splitLayoutLandscape = userPreferences.splitLayoutLandscape
    /** The current theme mode (light, dark, or system). */
    val themeMode = userPreferences.themeMode

    private val _inputMode = MutableStateFlow(InputMode.EZ)
    /** The currently active input mode. */
    val inputMode: StateFlow<InputMode> = _inputMode.asStateFlow()

    private val _asciiOnly = MutableStateFlow(false)
    /**
     * When true, the English layout suppresses word prediction and the
     * composing buffer: every letter keypress commits directly to the
     * target editor. Set automatically for password fields so that
     * password prefixes never appear in the candidate bar and never
     * feed the prediction trie.
     */
    val asciiOnly: StateFlow<Boolean> = _asciiOnly.asStateFlow()

    private val _emojiCategory = MutableStateFlow(0)
    /** The index of the currently selected emoji category. */
    val emojiCategory: StateFlow<Int> = _emojiCategory.asStateFlow()

    fun setEmojiCategory(index: Int) {
        _emojiCategory.value = index
    }

    // English prediction state.
    private val _englishBuffer = MutableStateFlow("")
    /** The current raw text typed in English mode. */
    val englishBuffer: StateFlow<String> = _englishBuffer.asStateFlow()

    private val _englishCandidates = MutableStateFlow<List<String>>(emptyList())
    /** List of predicted English words based on the current buffer. */
    val englishCandidates: StateFlow<List<String>> = _englishCandidates.asStateFlow()

    private val _shiftState = MutableStateFlow(ShiftState.NONE)
    /** Tri-state shift: none / one-shot / caps lock. */
    val shiftState: StateFlow<ShiftState> = _shiftState.asStateFlow()

    private val _composingText = MutableStateFlow("")
    /** The active keystroke sequence being typed in EZ mode. */
    val composingText: StateFlow<String> = _composingText.asStateFlow()

    private val _preEditBuffer = MutableStateFlow("")
    /** Characters already selected but not yet committed to the editor. */
    val preEditBuffer: StateFlow<String> = _preEditBuffer.asStateFlow()

    private val _candidates = MutableStateFlow<List<String>>(emptyList())
    /** The full list of character candidates for the current keystroke sequence. */
    val candidates: StateFlow<List<String>> = _candidates.asStateFlow()

    private val _candidatePage = MutableStateFlow(0)
    /** The current page index for the candidate list. */
    val candidatePage: StateFlow<Int> = _candidatePage.asStateFlow()

    private val _isSelecting = MutableStateFlow(false)
    /** Whether the user is currently navigating the candidate list. */
    val isSelecting: StateFlow<Boolean> = _isSelecting.asStateFlow()

    // Callback for InputMethodService to update the composing region
    var onUpdateComposingText: ((String) -> Unit)? = null

    private fun updateInlineComposing() {
        // Only push the already-selected pre-edit characters into the target
        // editor's composing region; the in-progress EZ roots live in the
        // candidate bar instead so unselected keystrokes never leak into the
        // user's document.
        onUpdateComposingText?.invoke(_preEditBuffer.value)
    }

    private val PAGE_SIZE = 10

    val pagedCandidates: StateFlow<List<String>> = combine(_candidates, _candidatePage) { list, page ->
        val start = page * PAGE_SIZE
        if (start < list.size) {
            list.drop(start).take(PAGE_SIZE)
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasNextPage: StateFlow<Boolean> = combine(_candidates, _candidatePage) { list, page ->
        (page + 1) * PAGE_SIZE < list.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasPrevPage: StateFlow<Boolean> = _candidatePage.map { it > 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun nextPage() {
        if ((_candidatePage.value + 1) * PAGE_SIZE < _candidates.value.size) {
            _candidatePage.value++
        }
    }

    fun prevPage() {
        if (_candidatePage.value > 0) {
            _candidatePage.value--
        }
    }

    /**
     * Advance the shift key's tri-state: NONE -> SHIFTED -> CAPS_LOCK -> NONE.
     * Quick double-tap thus lands in [ShiftState.CAPS_LOCK]; a third tap
     * releases it.
     */
    fun toggleShift() {
        _shiftState.value = when (_shiftState.value) {
            ShiftState.NONE -> ShiftState.SHIFTED
            ShiftState.SHIFTED -> ShiftState.CAPS_LOCK
            ShiftState.CAPS_LOCK -> ShiftState.NONE
        }
    }

    /**
     * Call after a letter or alternate key press has consumed the shift
     * state. If shift was a one-shot ([ShiftState.SHIFTED]), it falls back
     * to [ShiftState.NONE]; [ShiftState.CAPS_LOCK] is sticky and unaffected.
     */
    fun consumeShift() {
        if (_shiftState.value == ShiftState.SHIFTED) {
            _shiftState.value = ShiftState.NONE
        }
    }

    fun setInputMode(mode: InputMode) {
        _inputMode.value = mode
        if (mode != InputMode.EZ) {
            clearComposing()
        }
        if (mode != InputMode.ENGLISH) {
            clearEnglishBuffer()
        }
    }

    private var englishUpdateJob: kotlinx.coroutines.Job? = null

    fun onEnglishKeyPress(char: String) {
        val newBuffer = _englishBuffer.value + char
        _englishBuffer.value = newBuffer
        onUpdateComposingText?.invoke(newBuffer)
        updateEnglishCandidates(newBuffer)
    }

    fun onEnglishBackspace(): Boolean {
        if (_englishBuffer.value.isEmpty()) return false
        val newBuffer = _englishBuffer.value.dropLast(1)
        _englishBuffer.value = newBuffer
        onUpdateComposingText?.invoke(newBuffer)
        if (newBuffer.isEmpty()) {
            _englishCandidates.value = emptyList()
            englishUpdateJob?.cancel()
        } else {
            updateEnglishCandidates(newBuffer)
        }
        return true
    }

    /**
     * Takes the English buffer as-is for commit and clears state. The caller
     * is expected to push the returned text (plus any trailing separator)
     * into the input connection.
     */
    fun commitEnglishBuffer(): String {
        val text = _englishBuffer.value
        clearEnglishBuffer()
        return text
    }

    /**
     * Clears the buffer and returns the candidate string with the case of the
     * first character matched to what the user had typed (so `H` -> `Hello`,
     * `he` -> `hello`). Further learning/frequency logic could hook in here.
     */
    fun selectEnglishCandidate(word: String): String {
        val leading = _englishBuffer.value.firstOrNull()
        val cased = if (leading != null && leading.isUpperCase() && word.isNotEmpty()) {
            word.first().uppercase() + word.drop(1)
        } else {
            word
        }
        clearEnglishBuffer()
        return cased
    }

    private fun clearEnglishBuffer() {
        englishUpdateJob?.cancel()
        if (_englishBuffer.value.isNotEmpty()) {
            _englishBuffer.value = ""
            onUpdateComposingText?.invoke("")
        }
        _englishCandidates.value = emptyList()
    }

    private fun updateEnglishCandidates(prefix: String) {
        englishUpdateJob?.cancel()
        englishUpdateJob = viewModelScope.launch {
            _englishCandidates.value = EnglishDictionary.prefixLookup(appContext, prefix)
        }
    }

    private val _inputType = MutableStateFlow(android.text.InputType.TYPE_CLASS_TEXT)
    val inputType: StateFlow<Int> = _inputType.asStateFlow()

    /**
     * Updates the editor information and automatically switches input modes
     * based on the target text field's [android.text.InputType].
     */
    fun updateEditorInfo(info: android.view.inputmethod.EditorInfo?) {
        _inputType.value = info?.inputType ?: android.text.InputType.TYPE_CLASS_TEXT
        val imeOptions = info?.imeOptions ?: 0
        _inputMode.value = pickInputModeFor(_inputType.value)
        // asciiOnly tracks "no-prediction" mode. Set it regardless of the
        // current mode so that if the user cycles back into English
        // manually, the flag still suppresses prediction for the field
        // they're in.
        _asciiOnly.value = isAsciiOnlyFor(_inputType.value, imeOptions)
    }

    /**
     * Handles a key press in EZ input mode.
     * Appends the character to the composing buffer and updates the candidate list.
     */
    fun onKeyPress(key: String) {
        val newText = _composingText.value + key
        _composingText.value = newText
        _candidatePage.value = 0
        _isSelecting.value = false
        updateInlineComposing()
        updateCandidates(newText)
    }

    /**
     * Handles a backspace event.
     * Returns true if a character was removed from a buffer, false otherwise.
     */
    fun onBackspace(): Boolean {
        if (_composingText.value.isNotEmpty()) {
            val newText = _composingText.value.dropLast(1)
            _composingText.value = newText
            _candidatePage.value = 0
            _isSelecting.value = false
            updateInlineComposing()
            if (newText.isEmpty()) {
                _candidates.value = emptyList()
            } else {
                updateCandidates(newText)
            }
            return true
        } else if (_preEditBuffer.value.isNotEmpty()) {
            _preEditBuffer.value = _preEditBuffer.value.dropLast(1)
            _isSelecting.value = false
            updateInlineComposing()
            return true
        }
        return false
    }

    fun enterSelectionMode(): Boolean {
        if (_candidates.value.isNotEmpty()) {
            _isSelecting.value = true
            return true
        }
        return false
    }

    private var updateJob: kotlinx.coroutines.Job? = null

    private fun updateCandidates(keystroke: String) {
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            if (keystroke.isEmpty()) {
                _candidates.value = emptyList()
                return@launch
            }

            // Fetch all possible completions starting with this prefix.
            // FTS4 MATCH reserves "-", """, ":", "()", "*" — some EZ keystrokes
            // (e.g. the "-" root) can produce invalid MATCH patterns; we treat
            // any SQLite error as "no completions" rather than crashing.
            val allPossible = runCatching {
                db.dictionaryDao().getCharactersByPrefix("$keystroke*")
            }.getOrElse { emptyList() }

            if (allPossible.size == 1) {
                // Only one possible character or phrase exists for this sequence and its extensions.
                // Auto-select it to save the user a keystroke.
                selectCandidate(allPossible[0])
            } else {
                // Multiple possibilities exist.
                // Show exact matches first for the current sequence.
                val exact = db.dictionaryDao().getCharacters(keystroke)
                if (exact.isNotEmpty()) {
                    _candidates.value = exact
                } else {
                    // If no exact match yet, show the prefix matches as candidates.
                    _candidates.value = allPossible
                }
            }
        }
    }

    /**
     * Appends arbitrary text (e.g. a punctuation/symbol chosen from a key's
     * alternate popup in EZ mode) to the pre-edit buffer, so it stays in the
     * composing region instead of being committed to the editor immediately.
     * In-progress EZ roots in [_composingText] are left untouched; the user
     * can still finish or abandon the sequence.
     */
    fun appendToPreEdit(text: String) {
        if (text.isEmpty()) return
        _preEditBuffer.value = _preEditBuffer.value + text
        // A punctuation insert breaks any active candidate-selection
        // flow: otherwise the next digit keypress would still be read
        // as "select candidate N" against the stale candidate list.
        _isSelecting.value = false
        updateInlineComposing()
    }

    /**
     * Commits the selected candidate to the pre-edit buffer and clears the composing state.
     * Also updates the frequency of the candidate in the database for future sorting.
     *
     * @param candidate The character or phrase to select.
     * @return The updated pre-edit buffer string.
     */
    fun selectCandidate(candidate: String): String {
        val currentRoots = _composingText.value
        viewModelScope.launch {
            // Increment frequency for the exact keystroke the user committed on.
            db.dictionaryDao().incrementFrequencyExact(candidate, keystroke = currentRoots)
        }
        
        val newPreEdit = _preEditBuffer.value + candidate
        _preEditBuffer.value = newPreEdit
        _composingText.value = ""
        _candidates.value = emptyList()
        _candidatePage.value = 0
        _isSelecting.value = false
        updateInlineComposing()
        return newPreEdit
    }

    /**
     * Finalizes the current composition by combining the pre-edit and composing buffers.
     * Clears all temporary state and returns the full text to be committed.
     */
    fun commitAll(): String {
        val text = _preEditBuffer.value + _composingText.value
        clearComposing()
        return text
    }

    /**
     * Returns only the already-selected pre-edit characters, dropping any
     * in-progress EZ roots in [_composingText]. Used when switching input
     * modes: the user has committed to those characters by picking them,
     * but raw keystrokes that haven't resolved to a candidate yet would be
     * meaningless in the next layout.
     */
    fun commitPreEditOnly(): String {
        val text = _preEditBuffer.value
        clearComposing()
        return text
    }

    fun clearComposing() {
        _composingText.value = ""
        _preEditBuffer.value = ""
        _candidates.value = emptyList()
        _candidatePage.value = 0
        _isSelecting.value = false
        updateInlineComposing()
    }

    companion object {
        /**
         * Maps a text field's `android:inputType` to the layout that will be
         * most useful on first show. Numeric/phone/datetime fields land on
         * [InputMode.NUMBER]; text fields whose content is, by convention,
         * ASCII-only (email addresses, URIs, passwords) land on
         * [InputMode.ENGLISH]; everything else defaults to [InputMode.EZ].
         * The user can still cycle modes manually via the function-row key.
         *
         * Kept as a pure Int → InputMode function (no Android runtime state)
         * so it can be exercised by host-side JVM unit tests.
         */
        @JvmStatic
        fun pickInputModeFor(inputType: Int): InputMode {
            val inputClass = inputType and android.text.InputType.TYPE_MASK_CLASS
            if (inputClass == android.text.InputType.TYPE_CLASS_NUMBER ||
                inputClass == android.text.InputType.TYPE_CLASS_PHONE ||
                inputClass == android.text.InputType.TYPE_CLASS_DATETIME) {
                return InputMode.NUMBER
            }
            if (inputClass == android.text.InputType.TYPE_CLASS_TEXT) {
                val variation = inputType and android.text.InputType.TYPE_MASK_VARIATION
                val englishFriendly = when (variation) {
                    android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                    android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                    android.text.InputType.TYPE_TEXT_VARIATION_URI,
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                    android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> true
                    else -> false
                }
                if (englishFriendly) return InputMode.ENGLISH
            }
            return InputMode.EZ
        }

        /**
         * True when the English layout should run in "asciiOnly"
         * (no-prediction) mode: no composing buffer, no candidate bar
         * entries, no learned-frequency writes, so typed prefixes never
         * surface on screen or feed the prediction trie.
         *
         * Triggers, in order:
         *  * Password variations (`TYPE_TEXT_VARIATION_PASSWORD`,
         *    `_WEB_PASSWORD`, `_VISIBLE_PASSWORD`) — the hidden-field
         *    case that motivated this flag.
         *  * `TYPE_TEXT_FLAG_NO_SUGGESTIONS` on `inputType` — the app
         *    has explicitly opted out of suggestions (e.g., a search
         *    field for IDs, a 2FA code entry).
         *  * `IME_FLAG_NO_PERSONALIZED_LEARNING` on `imeOptions` — the
         *    app has asked the IME not to personalise / learn from this
         *    field (privacy-sensitive forms, incognito surfaces).
         *    Available since API 26, matching our `minSdk`.
         *
         * Email / URI fields do not trigger asciiOnly on their own —
         * they're still ASCII but benefit from word prediction.
         */
        @JvmStatic
        fun isAsciiOnlyFor(inputType: Int, imeOptions: Int = 0): Boolean {
            val inputClass = inputType and android.text.InputType.TYPE_MASK_CLASS
            if (inputClass == android.text.InputType.TYPE_CLASS_TEXT) {
                val variation = inputType and android.text.InputType.TYPE_MASK_VARIATION
                if (variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                    return true
                }
                if ((inputType and android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) {
                    return true
                }
            }
            if ((imeOptions and android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) {
                return true
            }
            return false
        }
    }
}
