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

package org.ghostsinthelab.app.rakurakuime.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.ghostsinthelab.app.rakurakuime.ui.theme.ThemeMode

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferences(private val context: Context) {

    companion object {
        private val KEY_VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        private val KEY_VIBRATION_INTENSITY = floatPreferencesKey("vibration_intensity")
        private val KEY_KEYBOARD_HEIGHT_SCALE = floatPreferencesKey("keyboard_height_scale")
        private val KEY_SPLIT_LAYOUT_LANDSCAPE = booleanPreferencesKey("split_layout_landscape")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val vibrationEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_VIBRATION_ENABLED] ?: true
    }

    val vibrationIntensity: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_VIBRATION_INTENSITY] ?: 0.5f
    }

    val keyboardHeightScale: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_KEYBOARD_HEIGHT_SCALE] ?: 1.0f
    }

    val splitLayoutLandscape: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SPLIT_LAYOUT_LANDSCAPE] ?: true
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE]
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.DYNAMIC
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_VIBRATION_ENABLED] = enabled }
    }

    suspend fun setVibrationIntensity(intensity: Float) {
        context.dataStore.edit { it[KEY_VIBRATION_INTENSITY] = intensity }
    }

    suspend fun setKeyboardHeightScale(scale: Float) {
        context.dataStore.edit { it[KEY_KEYBOARD_HEIGHT_SCALE] = scale }
    }

    suspend fun setSplitLayoutLandscape(split: Boolean) {
        context.dataStore.edit { it[KEY_SPLIT_LAYOUT_LANDSCAPE] = split }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }
}
