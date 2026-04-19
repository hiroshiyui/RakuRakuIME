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

enum class InputMode {
    EZ, NUMBER, ENGLISH, EMOJI
}

class KeyboardViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ImeDatabase.getDatabase(application)
    private val userPreferences = UserPreferences(application)
    private val appContext = application.applicationContext

    val vibrationEnabled = userPreferences.vibrationEnabled
    val vibrationIntensity = userPreferences.vibrationIntensity
    val keyboardHeightScale = userPreferences.keyboardHeightScale
    val splitLayoutLandscape = userPreferences.splitLayoutLandscape
    val themeMode = userPreferences.themeMode

    private val _inputMode = MutableStateFlow(InputMode.EZ)
    val inputMode: StateFlow<InputMode> = _inputMode.asStateFlow()

    private val _emojiCategory = MutableStateFlow(0)
    val emojiCategory: StateFlow<Int> = _emojiCategory.asStateFlow()

    fun setEmojiCategory(index: Int) {
        _emojiCategory.value = index
    }

    // English prediction state.
    private val _englishBuffer = MutableStateFlow("")
    val englishBuffer: StateFlow<String> = _englishBuffer.asStateFlow()

    private val _englishCandidates = MutableStateFlow<List<String>>(emptyList())
    val englishCandidates: StateFlow<List<String>> = _englishCandidates.asStateFlow()

    private val _isShifted = MutableStateFlow(false)
    val isShifted: StateFlow<Boolean> = _isShifted.asStateFlow()

    private val _composingText = MutableStateFlow("")
    val composingText: StateFlow<String> = _composingText.asStateFlow()

    private val _preEditBuffer = MutableStateFlow("")
    val preEditBuffer: StateFlow<String> = _preEditBuffer.asStateFlow()

    private val _candidates = MutableStateFlow<List<String>>(emptyList())
    val candidates: StateFlow<List<String>> = _candidates.asStateFlow()

    private val _candidatePage = MutableStateFlow(0)
    val candidatePage: StateFlow<Int> = _candidatePage.asStateFlow()

    private val _isSelecting = MutableStateFlow(false)
    val isSelecting: StateFlow<Boolean> = _isSelecting.asStateFlow()

    // Callback for InputMethodService to update the composing region
    var onUpdateComposingText: ((String) -> Unit)? = null

    private fun updateInlineComposing() {
        val text = _preEditBuffer.value + _composingText.value
        onUpdateComposingText?.invoke(text)
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

    fun toggleShift() {
        _isShifted.value = !_isShifted.value
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

    fun updateEditorInfo(info: android.view.inputmethod.EditorInfo?) {
        _inputType.value = info?.inputType ?: android.text.InputType.TYPE_CLASS_TEXT
        // Switch to NUMBER mode automatically if needed
        val inputClass = _inputType.value and android.text.InputType.TYPE_MASK_CLASS
        if (inputClass == android.text.InputType.TYPE_CLASS_NUMBER || 
            inputClass == android.text.InputType.TYPE_CLASS_PHONE) {
            _inputMode.value = InputMode.NUMBER
        } else {
            _inputMode.value = InputMode.EZ
        }
    }

    fun onKeyPress(key: String) {
        val newText = _composingText.value + key
        _composingText.value = newText
        _candidatePage.value = 0
        _isSelecting.value = false
        updateInlineComposing()
        updateCandidates(newText)
    }

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

            // Fetch all possible completions starting with this prefix
            val allPossible = db.dictionaryDao().getCharactersByPrefix("$keystroke*")

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

    fun selectCandidate(candidate: String): String {
        val currentRoots = _composingText.value
        viewModelScope.launch {
            // Increment frequency for the selected candidate
            // We pass currentRoots as the prefix to help narrow down which mapping was intended
            db.dictionaryDao().incrementFrequency(candidate, exactKeystroke = currentRoots, prefix = currentRoots)
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

    fun commitAll(): String {
        val text = _preEditBuffer.value + _composingText.value
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
}
