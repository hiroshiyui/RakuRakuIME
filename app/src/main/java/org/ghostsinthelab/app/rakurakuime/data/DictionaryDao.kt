package org.ghostsinthelab.app.rakurakuime.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DictionaryDao {
    @Query("SELECT character FROM dictionary WHERE keystroke = :keystroke ORDER BY frequency DESC")
    suspend fun getCharacters(keystroke: String): List<String>

    @Query("SELECT character FROM dictionary WHERE keystroke LIKE :keystrokePrefix || '%' ORDER BY frequency DESC LIMIT 50")
    suspend fun getCharactersByPrefix(keystrokePrefix: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DictionaryEntry>)

    @Query("SELECT COUNT(id) FROM dictionary")
    suspend fun count(): Int

    @Query("DELETE FROM dictionary")
    suspend fun clearAll()
}
