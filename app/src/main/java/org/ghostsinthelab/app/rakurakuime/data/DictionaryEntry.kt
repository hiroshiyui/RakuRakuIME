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
import androidx.room.Fts4
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "dictionary",
    indices = [Index(value = ["keystroke"])]
)
data class DictionaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val keystroke: String,
    val character: String,
    val frequency: Int = 0,
    /**
     * Static (corpus-derived) priority for single-character entries.
     * Seeded from MOE 85年字頻總表 via [FrequencyCsv]; 0 for phrase rows
     * or characters absent from the survey. See [DictionaryDao] for how
     * this participates in candidate ordering.
     */
    @ColumnInfo(name = "character_weight", defaultValue = "0")
    val characterWeight: Int = 0,
    /**
     * Static (corpus-derived) priority for phrase entries (詞目). Seeded
     * from MOE 85年詞頻總表; 0 for single-char rows or phrases absent
     * from the survey.
     */
    @ColumnInfo(name = "phrase_weight", defaultValue = "0")
    val phraseWeight: Int = 0,
)

@Entity(tableName = "dictionary_fts")
@Fts4(contentEntity = DictionaryEntry::class)
data class DictionaryFts(
    val keystroke: String,
    val character: String
)
