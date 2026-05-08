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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DictionaryDao {
    // Candidate-ordering policy (see also DictionaryEntry / FrequencyCsv):
    // 1. `frequency` (per-user learning) takes priority — once a user has
    //    selected a candidate, their behaviour outranks the static prior.
    // 2. Tied learning is broken by the corpus weights from MOE 字頻／詞頻.
    //    `character_weight` and `phrase_weight` are mutually exclusive on
    //    any given row (one is 0 by construction), so summing them gives
    //    a single static priority.
    // On a fresh install with all frequencies at 0, ordering effectively
    // collapses to corpus weight DESC, then primary-key insertion order.
    @Query(
        "SELECT character FROM dictionary " +
            "WHERE keystroke = :keystroke " +
            "ORDER BY frequency DESC, (character_weight + phrase_weight) DESC"
    )
    suspend fun getCharacters(keystroke: String): List<String>

    @Query("""
        SELECT dictionary.character
        FROM dictionary
        JOIN dictionary_fts ON dictionary.id = dictionary_fts.rowid
        WHERE dictionary_fts.keystroke MATCH :query
        ORDER BY dictionary.frequency DESC,
                 (dictionary.character_weight + dictionary.phrase_weight) DESC
        LIMIT 50
    """)
    suspend fun getCharactersByPrefix(query: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DictionaryEntry>)

    @Query("SELECT COUNT(id) FROM dictionary")
    suspend fun count(): Int

    @Query("DELETE FROM dictionary")
    suspend fun clearAll()

    /** Bump the frequency of the given character for a specific keystroke. */
    @Query("UPDATE dictionary SET frequency = frequency + 1 WHERE character = :character AND keystroke = :keystroke")
    suspend fun incrementFrequencyExact(character: String, keystroke: String)

    /** Bump the frequency of the given character for every keystroke matching the given prefix. */
    @Query("UPDATE dictionary SET frequency = frequency + 1 WHERE character = :character AND keystroke LIKE :prefix || '%'")
    suspend fun incrementFrequencyByPrefix(character: String, prefix: String)

    @Query("UPDATE dictionary SET frequency = 0")
    suspend fun resetFrequencies()

    /**
     * Returns up to [limit] characters that most often follow [prefix] in the
     * bundled phrase corpus, ordered by aggregate frequency. Drives the
     * post-selection next-character prediction strip in EZ mode: after the
     * user picks "信", we mine multi-character entries beginning with "信"
     * (e.g. 信件, 信箱, 信封) and surface their second characters.
     *
     * The LIKE scan is unindexed but only fires on candidate selection — not
     * per keystroke — and the corpus is ~130k rows, so it stays well under a
     * frame on modern devices.
     */
    @Query("""
        SELECT substr(character, :prefixLen + 1, 1) AS next_char
        FROM dictionary
        WHERE character LIKE :likePattern
          AND length(character) > :prefixLen
        GROUP BY next_char
        ORDER BY SUM(frequency) DESC,
                 SUM(phrase_weight) DESC,
                 next_char
        LIMIT :limit
    """)
    suspend fun nextCharactersAfter(likePattern: String, prefixLen: Int, limit: Int): List<String>
}
