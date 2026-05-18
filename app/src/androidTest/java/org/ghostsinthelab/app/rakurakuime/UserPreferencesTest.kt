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

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.ghostsinthelab.app.rakurakuime.data.UserPreferences
import org.ghostsinthelab.app.rakurakuime.data.dataStore
import org.ghostsinthelab.app.rakurakuime.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserPreferencesTest {

    private lateinit var ctx: Context
    private lateinit var prefs: UserPreferences

    @Before
    fun setup() = runBlocking {
        ctx = ApplicationProvider.getApplicationContext()
        // DataStore is a process-singleton; clear all keys so each test
        // observes the documented defaults rather than whatever a prior
        // test left behind.
        ctx.dataStore.edit { it.clear() }
        prefs = UserPreferences(ctx)
    }

    @Test
    fun defaults_matchDocumentedFallbacks() = runBlocking {
        assertEquals(true, prefs.vibrationEnabled.first())
        assertEquals(0.5f, prefs.vibrationIntensity.first(), 0.0001f)
        assertEquals(1.0f, prefs.keyboardHeightScale.first(), 0.0001f)
        assertEquals(true, prefs.splitLayoutLandscape.first())
        assertEquals(ThemeMode.DYNAMIC, prefs.themeMode.first())
    }

    @Test
    fun vibrationEnabled_roundTrip() = runBlocking {
        prefs.setVibrationEnabled(false)
        assertEquals(false, prefs.vibrationEnabled.first())
        prefs.setVibrationEnabled(true)
        assertEquals(true, prefs.vibrationEnabled.first())
    }

    @Test
    fun vibrationIntensity_roundTrip() = runBlocking {
        prefs.setVibrationIntensity(0.25f)
        assertEquals(0.25f, prefs.vibrationIntensity.first(), 0.0001f)
        prefs.setVibrationIntensity(0.75f)
        assertEquals(0.75f, prefs.vibrationIntensity.first(), 0.0001f)
    }

    @Test
    fun keyboardHeightScale_roundTrip() = runBlocking {
        prefs.setKeyboardHeightScale(1.5f)
        assertEquals(1.5f, prefs.keyboardHeightScale.first(), 0.0001f)
    }

    @Test
    fun splitLayoutLandscape_roundTrip() = runBlocking {
        prefs.setSplitLayoutLandscape(false)
        assertEquals(false, prefs.splitLayoutLandscape.first())
    }

    @Test
    fun themeMode_roundTrip() = runBlocking {
        prefs.setThemeMode(ThemeMode.SOLARIZED)
        assertEquals(ThemeMode.SOLARIZED, prefs.themeMode.first())
        prefs.setThemeMode(ThemeMode.DYNAMIC)
        assertEquals(ThemeMode.DYNAMIC, prefs.themeMode.first())
    }

    @Test
    fun themeMode_unrecognisedStoredValue_fallsBackToDynamic() = runBlocking {
        // Older builds may have written enum names that have since been
        // removed/renamed. The flow must shrug those off rather than
        // crash with IllegalArgumentException — DYNAMIC is the documented
        // fallback.
        val key = stringPreferencesKey("theme_mode")
        ctx.dataStore.edit { it[key] = "NOT_A_REAL_THEME_MODE" }
        assertEquals(ThemeMode.DYNAMIC, prefs.themeMode.first())
    }
}
