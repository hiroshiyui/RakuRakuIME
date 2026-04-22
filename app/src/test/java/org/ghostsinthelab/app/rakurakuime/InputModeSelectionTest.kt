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

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.ghostsinthelab.app.rakurakuime.ui.InputMode
import org.ghostsinthelab.app.rakurakuime.ui.KeyboardViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-side JVM tests for [KeyboardViewModel.pickInputModeFor]. The function
 * is intentionally pure (no Android runtime state) so the inputType →
 * InputMode mapping can be exercised without an emulator.
 */
class InputModeSelectionTest {

    @Test
    fun numberClass_goesToNumber() {
        assertEquals(InputMode.NUMBER, KeyboardViewModel.pickInputModeFor(InputType.TYPE_CLASS_NUMBER))
    }

    @Test
    fun numberWithDecimalFlag_goesToNumber() {
        val t = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        assertEquals(InputMode.NUMBER, KeyboardViewModel.pickInputModeFor(t))
    }

    @Test
    fun phoneClass_goesToNumber() {
        assertEquals(InputMode.NUMBER, KeyboardViewModel.pickInputModeFor(InputType.TYPE_CLASS_PHONE))
    }

    @Test
    fun datetimeClass_goesToNumber() {
        assertEquals(InputMode.NUMBER, KeyboardViewModel.pickInputModeFor(InputType.TYPE_CLASS_DATETIME))
    }

    @Test
    fun emailVariation_goesToEnglish() {
        val t = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        assertEquals(InputMode.ENGLISH, KeyboardViewModel.pickInputModeFor(t))
    }

    @Test
    fun webEmailVariation_goesToEnglish() {
        val t = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        assertEquals(InputMode.ENGLISH, KeyboardViewModel.pickInputModeFor(t))
    }

    @Test
    fun uriVariation_goesToEnglish() {
        val t = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        assertEquals(InputMode.ENGLISH, KeyboardViewModel.pickInputModeFor(t))
    }

    @Test
    fun passwordVariation_goesToEnglish() {
        val t = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        assertEquals(InputMode.ENGLISH, KeyboardViewModel.pickInputModeFor(t))
    }

    @Test
    fun visiblePasswordVariation_goesToEnglish() {
        val t = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        assertEquals(InputMode.ENGLISH, KeyboardViewModel.pickInputModeFor(t))
    }

    @Test
    fun webPasswordVariation_goesToEnglish() {
        val t = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        assertEquals(InputMode.ENGLISH, KeyboardViewModel.pickInputModeFor(t))
    }

    @Test
    fun plainText_defaultsToEz() {
        assertEquals(InputMode.EZ, KeyboardViewModel.pickInputModeFor(InputType.TYPE_CLASS_TEXT))
    }

    @Test
    fun multilineText_defaultsToEz() {
        val t = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        assertEquals(InputMode.EZ, KeyboardViewModel.pickInputModeFor(t))
    }

    @Test
    fun personNameVariation_defaultsToEz() {
        val t = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME
        assertEquals(InputMode.EZ, KeyboardViewModel.pickInputModeFor(t))
    }

    @Test
    fun zero_defaultsToEz() {
        assertEquals(InputMode.EZ, KeyboardViewModel.pickInputModeFor(0))
    }

    // --- isAsciiOnlyFor: password variations trigger no-prediction mode ---

    @Test
    fun passwordVariation_isAsciiOnly() {
        val t = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        assertTrue(KeyboardViewModel.isAsciiOnlyFor(t))
    }

    @Test
    fun webPasswordVariation_isAsciiOnly() {
        val t = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        assertTrue(KeyboardViewModel.isAsciiOnlyFor(t))
    }

    @Test
    fun visiblePasswordVariation_isAsciiOnly() {
        val t = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        assertTrue(KeyboardViewModel.isAsciiOnlyFor(t))
    }

    @Test
    fun emailVariation_isNotAsciiOnly() {
        // Email still benefits from prediction.
        val t = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        assertFalse(KeyboardViewModel.isAsciiOnlyFor(t))
    }

    @Test
    fun uriVariation_isNotAsciiOnly() {
        val t = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        assertFalse(KeyboardViewModel.isAsciiOnlyFor(t))
    }

    @Test
    fun plainText_isNotAsciiOnly() {
        assertFalse(KeyboardViewModel.isAsciiOnlyFor(InputType.TYPE_CLASS_TEXT))
    }

    @Test
    fun numberClass_isNotAsciiOnly() {
        assertFalse(KeyboardViewModel.isAsciiOnlyFor(InputType.TYPE_CLASS_NUMBER))
    }

    @Test
    fun noSuggestionsFlag_isAsciiOnly() {
        // App-declared opt-out via TYPE_TEXT_FLAG_NO_SUGGESTIONS must
        // engage asciiOnly even on a plain text field.
        val t = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        assertTrue(KeyboardViewModel.isAsciiOnlyFor(t))
    }

    @Test
    fun imeNoPersonalizedLearning_isAsciiOnly() {
        // App-declared opt-out via imeOptions must engage asciiOnly
        // regardless of inputType (privacy-sensitive / incognito forms).
        assertTrue(
            KeyboardViewModel.isAsciiOnlyFor(
                InputType.TYPE_CLASS_TEXT,
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
            )
        )
    }

    @Test
    fun imeNoPersonalizedLearning_overridesEmail() {
        // Even an email field honors IME_FLAG_NO_PERSONALIZED_LEARNING.
        val t = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        assertTrue(
            KeyboardViewModel.isAsciiOnlyFor(t, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        )
    }

    @Test
    fun imeOptionsWithoutPrivacyFlag_isNotAsciiOnly() {
        // Unrelated imeOptions bits (e.g. IME_ACTION_*) must not trigger.
        assertFalse(
            KeyboardViewModel.isAsciiOnlyFor(
                InputType.TYPE_CLASS_TEXT,
                EditorInfo.IME_ACTION_SEND,
            )
        )
    }
}
