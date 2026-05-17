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
interface UserPhraseDao {
    @Query("SELECT * FROM user_phrases ORDER BY created_at DESC, id DESC")
    suspend fun enumerateAll(): List<UserPhraseEntry>

    @Query("SELECT character FROM user_phrases WHERE keystroke = :keystroke ORDER BY created_at DESC")
    suspend fun getCharacters(keystroke: String): List<String>

    /**
     * Prefix lookup for user phrases. The table is small (human-scale) so a
     * `LIKE` scan is fine — no FTS shadow required.
     */
    @Query(
        "SELECT character FROM user_phrases " +
            "WHERE keystroke LIKE :prefix || '%' " +
            "ORDER BY length(keystroke) ASC, created_at DESC " +
            "LIMIT 50"
    )
    suspend fun getCharactersByPrefix(prefix: String): List<String>

    /**
     * Mirrors [DictionaryDao.nextCharactersAfter] but over the user table —
     * lets user phrases also drive the post-selection next-character strip.
     */
    @Query(
        """
        SELECT substr(character, :prefixLen + 1, 1) AS next_char
        FROM user_phrases
        WHERE character LIKE :likePattern
          AND length(character) > :prefixLen
        GROUP BY next_char
        ORDER BY MAX(created_at) DESC, next_char
        LIMIT :limit
        """
    )
    suspend fun nextCharactersAfter(likePattern: String, prefixLen: Int, limit: Int): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: UserPhraseEntry): Long

    @Query("DELETE FROM user_phrases WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM user_phrases WHERE character = :character AND keystroke = :keystroke")
    suspend fun deleteExact(character: String, keystroke: String)

    @Query("DELETE FROM user_phrases")
    suspend fun clearAll()

    @Query("SELECT COUNT(id) FROM user_phrases")
    suspend fun count(): Int
}
