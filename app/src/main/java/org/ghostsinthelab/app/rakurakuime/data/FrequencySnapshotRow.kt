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

/**
 * Lightweight projection of a `dictionary` row used by the Backup / Restore
 * manager. Skips the static corpus weight columns — backups only need to
 * preserve the user-derived `frequency`; the static priors come back for
 * free from the bundled asset DB on the next install.
 */
data class FrequencySnapshotRow(
    val keystroke: String,
    val character: String,
    val frequency: Int,
)
