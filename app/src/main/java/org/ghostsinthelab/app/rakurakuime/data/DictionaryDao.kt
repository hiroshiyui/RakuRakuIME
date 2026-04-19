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
    @Query("SELECT character FROM dictionary WHERE keystroke = :keystroke ORDER BY frequency DESC")
    suspend fun getCharacters(keystroke: String): List<String>

    @Query("""
        SELECT dictionary.character 
        FROM dictionary 
        JOIN dictionary_fts ON dictionary.id = dictionary_fts.rowid 
        WHERE dictionary_fts.keystroke MATCH :query 
        ORDER BY dictionary.frequency DESC 
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
}
