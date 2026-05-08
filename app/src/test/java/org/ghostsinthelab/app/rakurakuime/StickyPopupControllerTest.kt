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

import org.ghostsinthelab.app.rakurakuime.ui.keyboard.StickyPopupController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coordinates the keyboard's sticky popups (alternates, mode picker).
 * The contract is plain Kotlin, no Compose state — exercised on the host.
 */
class StickyPopupControllerTest {

    @Test
    fun freshController_isNotOpen() {
        assertFalse(StickyPopupController().isOpen)
    }

    @Test
    fun openExclusive_marksOpen() {
        val controller = StickyPopupController()
        controller.openExclusive {}
        assertTrue(controller.isOpen)
    }

    @Test
    fun dismiss_invokesRegisteredCallback_andClears() {
        val controller = StickyPopupController()
        var dismissed = 0
        controller.openExclusive { dismissed++ }
        controller.dismiss()
        assertEquals(1, dismissed)
        assertFalse(controller.isOpen)
    }

    @Test
    fun dismissTwice_invokesCallbackOnlyOnce() {
        // After dismiss the registration must clear, so a second dismiss
        // is a no-op — protects against double-fire when the user taps
        // both an item and outside the popup in quick succession.
        val controller = StickyPopupController()
        var dismissed = 0
        controller.openExclusive { dismissed++ }
        controller.dismiss()
        controller.dismiss()
        assertEquals(1, dismissed)
    }

    @Test
    fun openSecondPopup_dismissesFirst() {
        // openExclusive is the load-bearing primitive: opening the mode
        // picker while a key's alternates popup is up must close the
        // alternates so only one popup is ever visible.
        val controller = StickyPopupController()
        var firstDismissed = 0
        var secondDismissed = 0
        controller.openExclusive { firstDismissed++ }
        controller.openExclusive { secondDismissed++ }

        assertEquals(1, firstDismissed)
        assertEquals(0, secondDismissed)
        assertTrue(controller.isOpen)

        controller.dismiss()
        assertEquals(1, secondDismissed)
        assertFalse(controller.isOpen)
    }

    @Test
    fun dismissOnFreshController_isNoOp() {
        val controller = StickyPopupController()
        controller.dismiss() // must not throw
        assertFalse(controller.isOpen)
    }

    @Test
    fun dismissCallback_canSafelyReopen() {
        // Registration must clear *before* the callback runs, otherwise a
        // callback that opens a new popup would have its registration
        // immediately wiped by the outer dismiss.
        val controller = StickyPopupController()
        var reopenDismissed = 0
        controller.openExclusive {
            controller.openExclusive { reopenDismissed++ }
        }
        controller.dismiss()
        assertTrue(controller.isOpen)

        controller.dismiss()
        assertEquals(1, reopenDismissed)
        assertFalse(controller.isOpen)
    }
}
