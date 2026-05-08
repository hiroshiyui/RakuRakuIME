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

package org.ghostsinthelab.app.rakurakuime

import android.app.Application
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.ghostsinthelab.app.rakurakuime.ui.InputMode
import org.ghostsinthelab.app.rakurakuime.ui.KeyboardViewModel
import org.ghostsinthelab.app.rakurakuime.ui.ShiftState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [KeyboardViewModel] buffer operations that the UI
 * layer depends on — appending punctuation from the alternates popup into
 * the pre-edit buffer, and committing only the pre-edit portion when the
 * user switches layouts (dropping any in-progress EZ roots).
 *
 * Runs as an instrumented test because the ViewModel constructor pulls in
 * the Room DB and DataStore from [Application].
 */
@RunWith(AndroidJUnit4::class)
class KeyboardViewModelTest {

    private lateinit var viewModel: KeyboardViewModel

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        viewModel = KeyboardViewModel(app)
    }

    @Test
    fun appendToPreEdit_appendsWithoutClearingComposingRoots() {
        viewModel.onKeyPress("a")
        viewModel.appendToPreEdit("，")

        assertEquals("，", viewModel.preEditBuffer.value)
        // In-progress EZ root must survive the punctuation insert so the
        // user can still resolve it into a character.
        assertEquals("a", viewModel.composingText.value)
    }

    @Test
    fun appendToPreEdit_isIgnoredForEmptyText() {
        viewModel.appendToPreEdit("x")
        viewModel.appendToPreEdit("")

        assertEquals("x", viewModel.preEditBuffer.value)
    }

    @Test
    fun appendToPreEdit_concatenatesAcrossCalls() {
        viewModel.appendToPreEdit("「")
        viewModel.appendToPreEdit("」")

        assertEquals("「」", viewModel.preEditBuffer.value)
    }

    @Test
    fun appendToPreEdit_exitsSelectionMode() {
        // Simulate the user entering candidate-selection: pretend a
        // non-empty candidate list exists and space has been pressed.
        // (We can't call updateCandidates directly without hitting the
        // DB, so drive isSelecting via enterSelectionMode's side door:
        // it requires candidates.value to be non-empty. Instead, use
        // the real keypress path far enough to populate candidates.)
        viewModel.onKeyPress("a")
        // Wait briefly for the coroutine-driven candidate update to
        // land so enterSelectionMode can flip the flag.
        Thread.sleep(300)
        viewModel.enterSelectionMode()
        assertTrue(
            "precondition: isSelecting must be true before the punctuation insert",
            viewModel.isSelecting.value,
        )

        viewModel.appendToPreEdit("，")

        // Punctuation must break the selection flow so the next digit
        // press commits a digit instead of selecting candidate N.
        assertEquals(false, viewModel.isSelecting.value)
    }

    @Test
    fun commitPreEditOnly_returnsPreEditAndDropsIncompleteRoots() {
        viewModel.appendToPreEdit("你好")
        viewModel.onKeyPress("a")  // in-progress EZ root — should be dropped

        val committed = viewModel.commitPreEditOnly()

        assertEquals("你好", committed)
        assertEquals("", viewModel.preEditBuffer.value)
        assertEquals("", viewModel.composingText.value)
    }

    @Test
    fun commitPreEditOnly_returnsEmptyWhenNothingSelected() {
        viewModel.onKeyPress("a")  // only in-progress roots, nothing selected yet

        val committed = viewModel.commitPreEditOnly()

        assertEquals("", committed)
        assertEquals("", viewModel.composingText.value)
    }

    /**
     * Polls a state flow until [predicate] holds or the deadline elapses.
     * Used to wait for the async prediction job (DB-backed coroutine) to
     * settle without baking arbitrary `Thread.sleep` durations into the
     * assertion path.
     */
    private fun <T> awaitState(
        flow: kotlinx.coroutines.flow.StateFlow<T>,
        timeoutMs: Long = 1500,
        intervalMs: Long = 25,
        predicate: (T) -> Boolean,
    ): T {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate(flow.value)) return flow.value
            Thread.sleep(intervalMs)
        }
        return flow.value
    }

    @Test
    fun selectCandidate_populatesNextCharPredictions() {
        // Pick a high-coverage seed: 信 leads many phrases (信件, 信箱, …) so
        // the prediction strip must come back non-empty for any reasonable
        // shipped dictionary.
        viewModel.selectCandidate("信")

        val predictions = awaitState(viewModel.nextCharPredictions) { it.isNotEmpty() }
        assertTrue(
            "Expected non-empty next-char predictions after selecting '信', got $predictions",
            predictions.isNotEmpty(),
        )
        assertTrue(
            "Predictions must be single characters: $predictions",
            predictions.all { it.length == 1 },
        )
    }

    @Test
    fun selectCandidate_usesLastCharOfPhrase() {
        // Multi-char picks (phrase candidates) must seed predictions off the
        // last character — that's the boundary the user just landed on.
        viewModel.selectCandidate("人信")

        val predictions = awaitState(viewModel.nextCharPredictions) { it.isNotEmpty() }
        assertTrue(
            "Phrase pick should still produce predictions seeded by last char",
            predictions.isNotEmpty(),
        )
    }

    @Test
    fun onKeyPress_clearsPredictions() {
        viewModel.selectCandidate("信")
        awaitState(viewModel.nextCharPredictions) { it.isNotEmpty() }

        // Any new EZ keystroke starts a fresh composing sequence; the
        // opportunistic prediction strip must dismiss so the digit-root
        // shortcut conflict can't bite.
        viewModel.onKeyPress("a")

        assertTrue(
            "Predictions should be cleared on new keystroke, got ${viewModel.nextCharPredictions.value}",
            viewModel.nextCharPredictions.value.isEmpty(),
        )
    }

    @Test
    fun onBackspace_clearsPredictions() {
        viewModel.selectCandidate("信")
        awaitState(viewModel.nextCharPredictions) { it.isNotEmpty() }

        viewModel.onBackspace()

        assertTrue(
            "Predictions should be cleared on backspace",
            viewModel.nextCharPredictions.value.isEmpty(),
        )
    }

    @Test
    fun appendToPreEdit_clearsPredictions() {
        viewModel.selectCandidate("信")
        awaitState(viewModel.nextCharPredictions) { it.isNotEmpty() }

        viewModel.appendToPreEdit("，")

        assertTrue(
            "Punctuation insert should dismiss the prediction strip",
            viewModel.nextCharPredictions.value.isEmpty(),
        )
    }

    @Test
    fun setInputMode_nonEz_clearsPredictions() {
        viewModel.selectCandidate("信")
        awaitState(viewModel.nextCharPredictions) { it.isNotEmpty() }

        viewModel.setInputMode(InputMode.ENGLISH)

        assertTrue(
            "Switching out of EZ must drop the prediction strip alongside other composing state",
            viewModel.nextCharPredictions.value.isEmpty(),
        )
    }

    @Test
    fun selectCandidate_appendsToPreEditEvenWithoutComposingRoots() {
        // Picking from the prediction strip means composingText is empty.
        // The pick must still land in the pre-edit buffer; the no-keystroke
        // branch only skips the frequency increment, not the buffer update.
        viewModel.selectCandidate("信")
        viewModel.selectCandidate("件")

        assertEquals("信件", viewModel.preEditBuffer.value)
        assertEquals("", viewModel.composingText.value)
    }

    // --- Shift state machine ---------------------------------------

    @Test
    fun toggleShift_cyclesNoneShiftedCapsLockNone() {
        assertEquals(ShiftState.NONE, viewModel.shiftState.value)
        viewModel.toggleShift()
        assertEquals(ShiftState.SHIFTED, viewModel.shiftState.value)
        viewModel.toggleShift()
        assertEquals(ShiftState.CAPS_LOCK, viewModel.shiftState.value)
        viewModel.toggleShift()
        assertEquals(ShiftState.NONE, viewModel.shiftState.value)
    }

    @Test
    fun consumeShift_releasesOneShotOnly() {
        viewModel.toggleShift()
        assertEquals(ShiftState.SHIFTED, viewModel.shiftState.value)
        viewModel.consumeShift()
        assertEquals(
            "One-shot SHIFTED must drop to NONE after a single key press",
            ShiftState.NONE,
            viewModel.shiftState.value,
        )
    }

    @Test
    fun consumeShift_doesNotReleaseCapsLock() {
        viewModel.toggleShift()
        viewModel.toggleShift()
        assertEquals(ShiftState.CAPS_LOCK, viewModel.shiftState.value)
        viewModel.consumeShift()
        assertEquals(
            "CAPS_LOCK is sticky; consumeShift must leave it alone",
            ShiftState.CAPS_LOCK,
            viewModel.shiftState.value,
        )
    }

    @Test
    fun consumeShift_isNoOpWhenNone() {
        viewModel.consumeShift()
        assertEquals(ShiftState.NONE, viewModel.shiftState.value)
    }

    // --- English buffer flow ---------------------------------------

    @Test
    fun englishKeyPress_appendsAndUpdatesBuffer() {
        viewModel.onEnglishKeyPress("h")
        viewModel.onEnglishKeyPress("e")
        viewModel.onEnglishKeyPress("l")
        assertEquals("hel", viewModel.englishBuffer.value)
    }

    @Test
    fun englishBackspace_returnsTrueAndShortensBuffer() {
        viewModel.onEnglishKeyPress("h")
        viewModel.onEnglishKeyPress("i")
        val handled = viewModel.onEnglishBackspace()
        assertTrue("Backspace must report it consumed the event", handled)
        assertEquals("h", viewModel.englishBuffer.value)
    }

    @Test
    fun englishBackspace_returnsFalseWhenBufferEmpty() {
        // The IME relies on this signal to fall through to the editor's
        // own delete-char behavior. A spurious `true` here would swallow
        // the user's backspace.
        assertFalse(viewModel.onEnglishBackspace())
    }

    @Test
    fun englishBackspace_emptiesBufferClearsCandidates() {
        viewModel.onEnglishKeyPress("a")
        viewModel.onEnglishBackspace()
        assertEquals("", viewModel.englishBuffer.value)
        assertTrue(viewModel.englishCandidates.value.isEmpty())
    }

    @Test
    fun commitEnglishBuffer_returnsAndClearsBuffer() {
        viewModel.onEnglishKeyPress("h")
        viewModel.onEnglishKeyPress("i")
        val text = viewModel.commitEnglishBuffer()
        assertEquals("hi", text)
        assertEquals("", viewModel.englishBuffer.value)
    }

    @Test
    fun selectEnglishCandidate_lowercaseBuffer_returnsLowercaseWord() {
        viewModel.onEnglishKeyPress("h")
        viewModel.onEnglishKeyPress("e")
        val cased = viewModel.selectEnglishCandidate("hello")
        assertEquals("hello", cased)
        assertEquals("", viewModel.englishBuffer.value)
    }

    @Test
    fun selectEnglishCandidate_uppercaseLeading_capitalisesFirstChar() {
        // The UI passes through the case of the typed buffer; after a
        // shift+letter the leading char is uppercase, so the committed
        // word must be capitalised — that's the whole reason the buffer
        // preserves case end-to-end.
        viewModel.onEnglishKeyPress("H")
        viewModel.onEnglishKeyPress("e")
        val cased = viewModel.selectEnglishCandidate("hello")
        assertEquals("Hello", cased)
    }

    @Test
    fun selectEnglishCandidate_emptyBuffer_returnsWordVerbatim() {
        // Defensive contract: even with no buffer, the candidate string
        // must come back unchanged so the caller can commit it directly.
        val cased = viewModel.selectEnglishCandidate("hello")
        assertEquals("hello", cased)
    }

    // --- Emoji category ---------------------------------------------

    @Test
    fun setEmojiCategory_updatesIndex() {
        assertEquals(0, viewModel.emojiCategory.value)
        viewModel.setEmojiCategory(3)
        assertEquals(3, viewModel.emojiCategory.value)
    }

    // --- updateEditorInfo: input-mode + asciiOnly side effects -------

    @Test
    fun updateEditorInfo_numberField_switchesToNumberMode() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        viewModel.updateEditorInfo(info)
        assertEquals(InputMode.NUMBER, viewModel.inputMode.value)
        assertFalse(viewModel.asciiOnly.value)
    }

    @Test
    fun updateEditorInfo_passwordField_switchesToEnglishAndAsciiOnly() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        viewModel.updateEditorInfo(info)
        assertEquals(InputMode.ENGLISH, viewModel.inputMode.value)
        assertTrue(
            "Password fields must engage asciiOnly so the prefix never feeds prediction",
            viewModel.asciiOnly.value,
        )
    }

    @Test
    fun updateEditorInfo_imeNoPersonalizedLearning_engagesAsciiOnly() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        viewModel.updateEditorInfo(info)
        assertTrue(viewModel.asciiOnly.value)
    }

    @Test
    fun updateEditorInfo_nullInfo_defaultsToEzTextNoAsciiOnly() {
        // Some call paths (test screens, edge cases on older Android) hand
        // us a null EditorInfo; the contract is to fall back to a sensible
        // EZ default rather than crash.
        viewModel.updateEditorInfo(null)
        assertEquals(InputMode.EZ, viewModel.inputMode.value)
        assertFalse(viewModel.asciiOnly.value)
    }

    @Test
    fun setInputMode_nonEnglish_clearsEnglishBuffer() {
        viewModel.onEnglishKeyPress("a")
        viewModel.onEnglishKeyPress("b")
        viewModel.setInputMode(InputMode.EZ)
        assertEquals(
            "Switching out of ENGLISH must clear the buffer to avoid leaking it on next entry",
            "",
            viewModel.englishBuffer.value,
        )
    }

    // --- Pagination -------------------------------------------------

    @Test
    fun nextPage_prevPage_areBoundedByCandidateSize() {
        // With no candidates loaded the page index must stay clamped at 0
        // — no crash, no negative index, no off-the-end paging.
        assertEquals(0, viewModel.candidatePage.value)
        viewModel.nextPage()
        assertEquals(0, viewModel.candidatePage.value)
        viewModel.prevPage()
        assertEquals(0, viewModel.candidatePage.value)
    }

    @Test
    fun setInputMode_nonEz_clearsPreEdit() {
        // Guards against future regressions: clearComposing must still fire
        // on layout change so stale state doesn't leak into the next session.
        // Set up the fullest possible EZ state — pre-edit text, in-progress
        // roots with candidates, and active selection mode — so the
        // assertions catch any piece that a future refactor forgets to clear.
        viewModel.appendToPreEdit("abc")
        viewModel.onKeyPress("a")
        Thread.sleep(300)
        viewModel.enterSelectionMode()

        viewModel.setInputMode(InputMode.ENGLISH)

        assertEquals("", viewModel.preEditBuffer.value)
        assertTrue(viewModel.composingText.value.isEmpty())
        assertTrue(viewModel.candidates.value.isEmpty())
        assertEquals(false, viewModel.isSelecting.value)
    }
}
