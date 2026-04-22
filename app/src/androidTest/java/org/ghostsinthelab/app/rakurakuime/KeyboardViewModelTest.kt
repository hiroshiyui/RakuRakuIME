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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.ghostsinthelab.app.rakurakuime.ui.InputMode
import org.ghostsinthelab.app.rakurakuime.ui.KeyboardViewModel
import org.junit.Assert.assertEquals
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

    @Test
    fun setInputMode_nonEz_clearsPreEdit() {
        // Guards against future regressions: clearComposing must still fire
        // on layout change so stale state doesn't leak into the next session.
        viewModel.appendToPreEdit("abc")
        viewModel.setInputMode(InputMode.ENGLISH)

        assertEquals("", viewModel.preEditBuffer.value)
        assertTrue(viewModel.composingText.value.isEmpty())
    }
}
