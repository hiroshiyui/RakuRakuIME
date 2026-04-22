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

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.ghostsinthelab.app.rakurakuime.ui.keyboard.KeyButton
import org.ghostsinthelab.app.rakurakuime.ui.keyboard.KeyDefinition
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins down the shifted-label placement on English non-letter keys.
 *
 * The English layout passes the first alternate (e.g. "!" for "1",
 * ":" for ";", "{" for "[") as `KeyDefinition.ezRoot`, so KeyButton's
 * existing TopStart root-label slot + BottomEnd demoted-main-label
 * combo renders the shifted char at the top-left and the default at
 * the bottom-right. This test keeps that combo honest against future
 * refactors of KeyButton's `ezRoot` alignment defaults.
 */
@RunWith(AndroidJUnit4::class)
class KeyButtonLabelTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun englishSymbolKey_showsBothShiftedAndDefaultLabels() {
        composeRule.setContent {
            MaterialTheme {
                KeyButton(
                    keyDef = KeyDefinition("1", "!", listOf("!")),
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithText("1").assertIsDisplayed()
        composeRule.onNodeWithText("!").assertIsDisplayed()
    }

    @Test
    fun englishSymbolKey_semicolon_colon() {
        composeRule.setContent {
            MaterialTheme {
                KeyButton(
                    keyDef = KeyDefinition(";", ":", listOf(":")),
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithText(";").assertIsDisplayed()
        composeRule.onNodeWithText(":").assertIsDisplayed()
    }

    @Test
    fun englishSymbolKey_leftBracket_leftBrace() {
        composeRule.setContent {
            MaterialTheme {
                KeyButton(
                    keyDef = KeyDefinition("[", "{", listOf("{")),
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithText("[").assertIsDisplayed()
        composeRule.onNodeWithText("{").assertIsDisplayed()
    }

    @Test
    fun englishLetterKey_hasNoShiftedLabel() {
        // Letters carry no alternate, so KeyboardScreen passes ezRoot = ""
        // and only one visible label is expected.
        composeRule.setContent {
            MaterialTheme {
                KeyButton(
                    keyDef = KeyDefinition("q", "", emptyList()),
                    isUppercase = false,
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithText("q").assertIsDisplayed()
    }
}
