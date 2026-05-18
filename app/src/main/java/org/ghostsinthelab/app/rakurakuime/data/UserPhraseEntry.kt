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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User-defined phrase. Unlike [DictionaryEntry] rows (which come from the
 * bundled corpus and carry a learned `frequency` and static corpus weights),
 * a user phrase is an explicit addition by the end user and is always ranked
 * ahead of corpus candidates for matching keystrokes.
 *
 * Stored in a separate table so the bundled corpus / FTS shadow can be
 * rebuilt or re-imported without disturbing user data, and so backup /
 * restore / wipe operations have a clean target.
 */
@Entity(
    tableName = "user_phrases",
    indices = [
        Index(value = ["keystroke"]),
        Index(value = ["character", "keystroke"], unique = true),
    ],
)
data class UserPhraseEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val character: String,
    val keystroke: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    /**
     * Per-row use counter, bumped on commit. Used to break ties between
     * multiple user phrases sharing a prefix — the corpus side has had
     * learned frequencies since v0, this column does the same for the
     * user table from v5 on. Older databases (v4) get the column with
     * `DEFAULT 0` via [MIGRATION_4_5]; the column has no effect on the
     * "always rank ahead of corpus" policy, it only sorts user phrases
     * among themselves.
     */
    @ColumnInfo(defaultValue = "0") val frequency: Long = 0,
)
